// E2E 환경(run.mjs가 쓴 build/e2e/run.json — 비밀 없음)과 하네스 제어. 토큰은 상태 디렉터리의 파일에서 읽는다(인자·환경 변수 아님).
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

interface Run {
  baseUrl: string;
  controlUrl: string;
  tokenDir: string;
  streamLength: Record<string, number>;
  projects: Record<string, string>;
}

const run = JSON.parse(readFileSync(resolve(import.meta.dirname, '../../build/e2e/run.json'), 'utf8')) as Run;
export const baseUrl = run.baseUrl;

/** 그 프로젝트의 테넌트 한 벌(GA01 → GA01A 등). */
export function tenantOf(projectName: string, base: 'GA01' | 'GA02' | 'GA03' | 'GA04' | 'GA05'): string {
  const suffix = run.projects[projectName];
  if (suffix === undefined) throw new Error(`unknown project ${projectName}`);
  return base + suffix;
}

export function streamLength(tenant: string): number {
  const n = run.streamLength[tenant];
  if (n === undefined) throw new Error(`unknown tenant ${tenant}`);
  return n;
}

export type TokenKind = 'agent' | 'unlinked' | 'expired';

export function token(tenant: string, kind: TokenKind): string {
  return readFileSync(resolve(run.tokenDir, `${tenant}-${kind}.jwt`), 'utf8').trim();
}

export function allTokens(): string[] {
  return Object.keys(run.streamLength).flatMap((t) => (['agent', 'unlinked', 'expired'] as const).map((k) => token(t, k)));
}

async function control(path: string): Promise<void> {
  const r = await fetch(run.controlUrl + path, { method: 'POST' });
  if (r.status !== 204) throw new Error(`harness control ${path.split('/')[1] ?? ''} answered ${String(r.status)}`);
}

/** 피드 머리를 seq까지 열고, 포털이 그 seq까지 적용할 때까지 기다린다(/api/v1/me의 asOf.seq — 브라우저 밖). */
export async function advance(tenant: string, seq: number): Promise<void> {
  await control(`/head/${tenant}/${String(seq)}`);
  const bearer = token(tenant, 'agent');
  const end = Date.now() + 90_000;
  for (;;) {
    const r = await fetch(`${baseUrl}/api/v1/me`, { headers: { Authorization: `Bearer ${bearer}` } });
    if (r.ok) {
      const body = (await r.json()) as { asOf: { seq: number } };
      if (body.asOf.seq >= seq) return;
    }
    if (Date.now() > end) throw new Error(`tenant did not reach seq ${String(seq)}`);
    await new Promise((ok) => setTimeout(ok, 500));
  }
}

export async function failFeed(tenant: string, on: boolean): Promise<void> {
  await control(`/fail/${tenant}/${on ? 'on' : 'off'}`);
}

/** 엔진 시뮬레이션 스텁의 상태(포털 Phase 5 ⑨): down 503 · up/fast 정상 · slow 읽기 시간 초과보다 길게 · broken 스키마 위반 200. */
export async function simEngine(tenant: string, mode: 'down' | 'up' | 'slow' | 'fast' | 'broken'): Promise<void> {
  await control(`/sim/${tenant}/${mode}`);
}

/** CLI 제어(포털 Phase 6 계획 Q9): 하네스가 실 CLI를 자식 프로세스로 띄우고 종료 코드만 돌려준다(출력은 버린다). */
async function cli(path: string): Promise<number> {
  const r = await fetch(`${run.controlUrl}/cli/${path}`, { method: 'POST' });
  if (r.status !== 200) throw new Error(`harness cli control answered ${String(r.status)}`);
  return Number(await r.text());
}

/** {@code ingest rebuild --tenant=T}. 적용 중인 워커가 잠금을 쥐고 있으면 CLI가 거부(2 — "try again")하므로 몇 번 다시 친다 — 운영자가 하는 것과 같다. */
export async function rebuild(tenant: string): Promise<number> {
  let code = -1;
  for (let i = 0; i < 5; i++) {
    code = await cli(`${tenant}/rebuild`);
    if (code !== 2) return code;
    await new Promise((ok) => setTimeout(ok, 1_000));
  }
  return code;
}

/** {@code inquiry answer --tenant=T --id=<id> --answer-file=<상태 디렉터리의 파일>} — 답변 문장은 하네스의 허구 고정 문장({@link ANSWER}). */
export async function answer(tenant: string, inquiryId: string): Promise<number> {
  return cli(`${tenant}/answer/${inquiryId}`);
}

/** 하네스의 답변 파일 문장(WebE2eHarness.ANSWER — 끝 줄바꿈 하나는 CLI가 뺀다). */
export const ANSWER = '확인 결과 지급 내역은 명세와 같습니다. 추가로 궁금한 점은 이 화면으로 다시 남겨 주세요.';

/** 브라우저 밖 조회(설계사 토큰) — 전후 비교용. 응답은 누출 검사의 대상이 아니므로 본문이 실리는 경로(문의 상세)에는 쓰지 않는다. */
export async function me<T>(tenant: string, path: string): Promise<T> {
  if (path.startsWith('inquiries/')) throw new Error('read inquiry details through the screen');
  const url = path === '' ? `${baseUrl}/api/v1/me` : `${baseUrl}/api/v1/me/${path}`;
  const r = await fetch(url, { headers: { Authorization: `Bearer ${token(tenant, 'agent')}` } });
  if (!r.ok) throw new Error(`GET /api/v1/me/${path.split('?')[0] ?? ''} answered ${String(r.status)}`);
  return (await r.json()) as T;
}

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
export function tenantOf(projectName: string, base: 'GA01' | 'GA02' | 'GA03' | 'GA04'): string {
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

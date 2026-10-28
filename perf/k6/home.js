// k6 — 홈 화면 묶음(포털 Phase 8 지시문 ⑤, 계획 Q13·P-2·P-23, 설계서 §13 v1.11 예시값). compose 데모 스택(docker/compose.yaml --profile demo)을 상대로
// 설계사 30명이 동시에 홈을 연다: 화면이 부르는 다섯 콜(/me·이번 달·한도·이번 달 분급·최근 알림 3)을 한 묶음으로(http.batch — 브라우저처럼 동시에).
// 토큰은 데모 렐름의 k6 클라이언트(Direct Access Grants — 데모 전용)로 setup에서 받는다. 비밀번호는 README의 허구 값을 파일로 읽는다(인자·환경 0).
// 판정(예시값 — §13): 콜 p95 ≤ 300 ms, 묶음 p95 ≤ 800 ms, 실패 0. 요약은 /out/summary.json(perf.yml이 아티팩트로).
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = 'http://localhost:8080';
const TOKEN_URL = 'http://localhost:8180/realms/demo/protocol/openid-connect/token';
const AGENTS = Array.from({ length: 30 }, (_, i) => `a-${2001 + i}`);
const README = open('/repo/README.md');
const PASSWORD = (/데모 비밀번호: `([^`]+)`/.exec(README) || [])[1];

const CALLS = ['me', 'month', 'limits', 'deferrals', 'notifications'];
const callTrends = Object.fromEntries(CALLS.map((c) => [c, new Trend(`call_${c}`, true)]));
const bundle = new Trend('home_bundle', true);
const failures = new Rate('call_failures');

export const options = {
  scenarios: {
    home: { executor: 'constant-vus', vus: AGENTS.length, duration: __ENV.DURATION || '3m' },
  },
  thresholds: {
    'http_req_duration{call:me}': ['p(95)<=300'],
    'http_req_duration{call:month}': ['p(95)<=300'],
    'http_req_duration{call:limits}': ['p(95)<=300'],
    'http_req_duration{call:deferrals}': ['p(95)<=300'],
    'http_req_duration{call:notifications}': ['p(95)<=300'],
    home_bundle: ['p(95)<=800'],
    call_failures: ['rate==0'],
  },
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max', 'count'],
};

/** KST 이번 달 YYYYMM(브라우저 홈과 같은 규칙 — 실제 시계). */
function thisYm() {
  const kst = new Date(Date.now() + 9 * 3600 * 1000);
  return `${kst.getUTCFullYear()}${String(kst.getUTCMonth() + 1).padStart(2, '0')}`;
}

export function setup() {
  if (!PASSWORD) throw new Error('README has no demo password line');
  return {
    tokens: AGENTS.map((username) => {
      const r = http.post(TOKEN_URL, { grant_type: 'password', client_id: 'k6', username, password: PASSWORD, scope: 'openid' });
      if (r.status !== 200) throw new Error(`token for ${username}: ${r.status}`);
      return r.json('access_token');
    }),
  };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const ym = thisYm();
  const params = (call) => ({ headers: { Authorization: `Bearer ${token}` }, tags: { call } });
  const started = Date.now();
  const responses = http.batch([
    ['GET', `${BASE}/api/v1/me`, null, params('me')],
    ['GET', `${BASE}/api/v1/me/earnings/${ym}`, null, params('month')],
    ['GET', `${BASE}/api/v1/me/limits?status=ALL`, null, params('limits')],
    ['GET', `${BASE}/api/v1/me/deferrals?from=${ym}&to=${ym}`, null, params('deferrals')],
    ['GET', `${BASE}/api/v1/me/notifications?size=3`, null, params('notifications')],
  ]);
  bundle.add(Date.now() - started);
  responses.forEach((r, i) => {
    callTrends[CALLS[i]].add(r.timings.duration);
    // 이번 달 행이 없는 설계사는 404가 정상 응답이다(화면 "이번 달 내역이 아직 없습니다")
    const ok = r.status === 200 || (CALLS[i] === 'month' && r.status === 404);
    failures.add(!ok);
    check(r, { [`${CALLS[i]} answered`]: () => ok });
  });
}

export function handleSummary(data) {
  const pick = (name) => {
    const m = data.metrics[name];
    return m ? { p50: m.values.med, p95: m.values['p(95)'], p99: m.values['p(99)'], max: m.values.max, count: m.values.count } : null;
  };
  const out = {
    vus: AGENTS.length,
    calls: Object.fromEntries(CALLS.map((c) => [c, pick(`call_${c}`)])),
    bundle: pick('home_bundle'),
    failures: data.metrics.call_failures ? data.metrics.call_failures.values.rate : null,
    thresholds: Object.fromEntries(Object.entries(data.metrics).filter(([, m]) => m.thresholds).map(([k, m]) => [k, Object.values(m.thresholds).every((t) => t.ok)])),
  };
  return { '/out/summary.json': JSON.stringify(out, null, 2), stdout: `${JSON.stringify(out, null, 2)}\n` };
}

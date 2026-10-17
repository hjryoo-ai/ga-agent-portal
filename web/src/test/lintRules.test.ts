// @vitest-environment node
// 화면 린트 규칙의 시험(포털 Phase 4 계획 ④-b, C1·C2): 실제 eslint.config.js로 src/__lint__/ 아래 임시 파일을 린트한다(타입 정보 포함).
// 규칙마다 걸려야 하는 것과 걸리지 않아야 하는 것을 둘 다 본다 — 아무것도 걸지 않는 규칙도, 모든 것을 거는 규칙도 실패한다.
import { ESLint } from 'eslint';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { MONEY_EXEMPT } from '../../eslint/plugin.js';

const web = resolve(import.meta.dirname, '../..');
const dir = join(web, 'src/__lint__');

const cases: Record<string, string> = {
  'moneyPlus.ts': `import type { components } from '../gen/portal-api';\nexport const f = (m: components['schemas']['MonthSummary']) => (m.gross as unknown as number) + 1;\n`,
  'moneyNumber.ts': `import type { components } from '../gen/portal-api';\nexport const f = (m: components['schemas']['MonthSummary']) => Number(m.gross);\n`,
  'moneyCompare.ts': `import type { components } from '../gen/portal-api';\nexport const f = (m: components['schemas']['MonthSummary']) => m.net !== undefined && m.net > m.gross;\n`,
  'moneyTemplate.ts': `import type { components } from '../gen/portal-api';\nexport const f = (m: components['schemas']['MonthSummary']) => \`\${String(m.gross)}\`;\n`,
  'moneyMath.ts': `import type { components } from '../gen/portal-api';\n// @ts-expect-error Won is not a number\nexport const f = (m: components['schemas']['MonthSummary']) => Math.abs(m.gross);\n`,
  'moneyUnary.ts': `import type { Won } from '../money/won';\nexport const f = (w: Won) => -w;\n`,
  'moneyPresence.ts': `import type { components } from '../gen/portal-api';\nexport const f = (m: components['schemas']['MonthSummary']) => m.net !== undefined;\n`,
  'moneyOk.ts': `import type { components } from '../gen/portal-api';\nimport { isZero, present } from '../money/won';\nexport const f = (m: components['schemas']['MonthSummary'], n: number) => [isZero(m.adjustment), present(m.net), n + 1, m.closeYm.length > 0];\nexport const g = (items: components['schemas']['MonthSummary'][]) => items.map((m) => m.gross);\n`,
  'hangul.tsx': `export const C = () => <p>정정</p>;\nexport const s = '확정';\n`,
  'hangulComment.ts': `// 주석은 한국어여도 된다\nexport const s = 'ok';\n`,
  'network.ts': `export const f = () => fetch('/api/v1/me');\nexport const g = () => window.localStorage.getItem('x');\nexport const h = () => navigator.serviceWorker;\n`,
  'clientImport.ts': `import createClient from 'openapi-fetch';\nexport const c = createClient();\n`,
};

let results: Map<string, string[]>;

beforeAll(async () => {
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
  for (const [name, text] of Object.entries(cases)) writeFileSync(join(dir, name), text);
  const eslint = new ESLint({ cwd: web });
  const out = await eslint.lintFiles(['src/__lint__/**/*.{ts,tsx}']);
  results = new Map(out.map((r) => [r.filePath.slice(dir.length + 1), r.messages.map((x) => x.ruleId ?? `fatal: ${x.message}`)]));
}, 120_000);

afterAll(() => {
  rmSync(dir, { recursive: true, force: true });
});

const rules = (name: string) => results.get(name) ?? [];

describe('portal/no-money-arithmetic', () => {
  it.each(['moneyPlus.ts', 'moneyNumber.ts', 'moneyCompare.ts', 'moneyTemplate.ts', 'moneyMath.ts', 'moneyUnary.ts', 'moneyPresence.ts'])('%s is reported', (name) => {
    expect(rules(name)).toContain('portal/no-money-arithmetic');
  });

  it('allows isZero, present, plain numbers, non-money fields and passing money along', () => {
    expect(rules('moneyOk.ts')).toEqual([]);
  });

  it('the exemption list is closed and every entry exists and converts Won', () => {
    expect(MONEY_EXEMPT).toEqual(['src/money/won.ts']);
    for (const f of MONEY_EXEMPT) {
      expect(existsSync(join(web, f)), f).toBe(true);
      expect(readFileSync(join(web, f), 'utf8')).toContain('as unknown as number');
    }
  });
});

describe('portal/no-hangul-literal', () => {
  it('reports Korean string literals and JSX text', () => {
    expect(rules('hangul.tsx').filter((r) => r === 'portal/no-hangul-literal')).toHaveLength(2);
  });

  it('allows Korean comments', () => {
    expect(rules('hangulComment.ts')).toEqual([]);
  });
});

describe('network and storage', () => {
  it('reports fetch, browser storage and service worker', () => {
    const r = rules('network.ts');
    expect(r.filter((x) => x === 'no-restricted-globals')).toHaveLength(1);
    expect(r.filter((x) => x === 'no-restricted-properties')).toHaveLength(2);
  });

  it('openapi-fetch is imported only in src/api/client.ts', () => {
    expect(rules('clientImport.ts')).toContain('no-restricted-imports');
  });
});

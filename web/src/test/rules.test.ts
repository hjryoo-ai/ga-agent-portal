// @vitest-environment node
// 입력 규칙(포털 Phase 5 계획 Q12): 화면 검사는 계약에서 생성한 규칙 파일의 데이터다 — 잠금 파일이 그 바이트를 고정하고, 규칙은 계약의 pattern 그대로다.
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { body, check, validRetention } from '../sim/rules';

const web = resolve(import.meta.dirname, '../..');

describe('generated input rules', () => {
  it('the rules file is the one the lock pins', () => {
    const lock = JSON.parse(readFileSync(resolve(web, 'contracts-client.lock.json'), 'utf8')) as { rules: string; rulesSha256: string };
    expect(lock.rules).toBe('src/gen/portal-api.rules.json');
    expect(createHash('sha256').update(readFileSync(resolve(web, lock.rules))).digest('hex')).toBe(lock.rulesSha256);
  });

  it('new contract: required, digit strings, real-looking dates', () => {
    expect(check('SimNewContractRequest', { productKey: 'WHOLE-LIFE-20Y', monthlyPremium: '150000', contractDate: '2026-08-01' })).toEqual({});
    expect(check('SimNewContractRequest', {})).toEqual({ productKey: 'required', monthlyPremium: 'required', contractDate: 'required' });
    expect(
      check('SimNewContractRequest', { productKey: ' x', monthlyPremium: '0150000', contractDate: '2026-8-1', paymentTermMonths: '10000', insurerCd: 'x'.repeat(65) }),
    ).toEqual({ productKey: 'invalid', monthlyPremium: 'invalid', contractDate: 'invalid', paymentTermMonths: 'invalid', insurerCd: 'invalid' });
    expect(check('SimNewContractRequest', { productKey: 'K', monthlyPremium: '1000000000000', contractDate: '2026-08-01' })).toEqual({ monthlyPremium: 'invalid' });
  });

  it('policy event: REDUCE makes the new premium required (the contract if/then), the key is 16 hex', () => {
    const base = { policyKey: '0123456789abcdef', asOfDate: '2027-02-15' };
    expect(check('SimPolicyEventRequest', { ...base, eventType: 'CANCEL' })).toEqual({});
    expect(check('SimPolicyEventRequest', { ...base, eventType: 'REDUCE' })).toEqual({ newMonthlyPremium: 'required' });
    expect(check('SimPolicyEventRequest', { ...base, eventType: 'REDUCE', newMonthlyPremium: '100000' })).toEqual({});
    expect(check('SimPolicyEventRequest', { ...base, policyKey: 'POL-2026-0001', eventType: 'NOPE' })).toEqual({ policyKey: 'invalid', eventType: 'invalid' });
  });

  it('bodies keep strings as typed and drop empty optional fields', () => {
    expect(body('SimNewContractRequest', { productKey: 'K', monthlyPremium: '150000', contractDate: '2026-08-01', paymentTermMonths: '', insurerCd: '' })).toEqual({
      productKey: 'K',
      monthlyPremium: '150000',
      contractDate: '2026-08-01',
    });
  });

  it('retention p: 1..100 integers only', () => {
    expect(['1', '30', '99', '100'].every(validRetention)).toBe(true);
    expect(['0', '101', '030', '3.5', '', ' 30'].some(validRetention)).toBe(false);
  });
});

// 금액 컴포넌트(C3): 정수 형식 + 상태 라벨이 늘 함께, 확인 중이면 흐린 표식.
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ledgerLabel, monthLabel } from '../labels/labels';
import { Amount } from '../money/Amount';
import type { Won } from '../money/won';

const won = (n: number) => n as unknown as Won;

describe('Amount', () => {
  it('renders the integer with a separator and its status label', () => {
    render(<Amount value={won(1740600)} label={monthLabel('PAID')} />);
    const amount = screen.getByText('1,740,600원').closest('.amount');
    expect(amount?.textContent).toBe('1,740,600원지급완료');
    expect(amount?.classList.contains('amount--uncertain')).toBe(false);
  });

  it('marks uncertain amounts and keeps the label', () => {
    render(<Amount value={won(-720000)} label={ledgerLabel()} uncertain size="large" />);
    const amount = screen.getByText('-720,000원').closest('.amount');
    expect(amount?.className).toBe('amount amount--large amount--uncertain');
    expect(amount?.textContent).toContain('원장 기준');
  });
});

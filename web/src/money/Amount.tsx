// 금액 표시(포털 Phase 4 계획 ④-d, C3): 금액은 이 컴포넌트로만 화면에 나온다(Won은 ReactNode가 아니다). 상태 라벨은 필수 — StatusLabel은
// labels.ts의 함수로만 만들어진다. 확인 중(DRIFT)이면 흐리게(AA 대비는 유지 — 색만 연하게, 점선 밑줄).
import { fill, m } from '../messages';
import type { StatusLabel } from '../labels/labels';
import { formatWon, type Won } from './won';

export interface AmountProps {
  value: Won;
  label: StatusLabel;
  size?: 'large' | 'normal';
  uncertain?: boolean;
}

export function Amount({ value, label, size = 'normal', uncertain = false }: AmountProps) {
  const classes = ['amount', `amount--${size}`, uncertain ? 'amount--uncertain' : ''].filter((c) => c !== '').join(' ');
  return (
    <span className={classes}>
      <span className="amount__value">{fill(m.fill.won, { value: formatWon(value) })}</span>
      <span className="amount__label">{label}</span>
    </span>
  );
}

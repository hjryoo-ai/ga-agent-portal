// 한도 판정 문구(Phase 3 수용 심사 Q4): 정책 이름이 아니라 사실대로 "한도 초과 삭감" — DEFER_AFTER_FY에는 보조 문구(현재 엔진은 이연하지 않는다).
import { ledgerLabel, type StatusLabel } from '../labels/labels';
import { m } from '../messages';
import { Amount } from '../money/Amount';
import type { Won } from '../money/won';

export function LimitCut({ amount, policy, label, uncertain = false }: { amount: Won; policy: string | undefined; label?: StatusLabel; uncertain?: boolean }) {
  return (
    <span className="limit-cut" data-testid="limit-cut">
      <span>{m.limitCut.label}</span> <Amount value={amount} label={label ?? ledgerLabel()} uncertain={uncertain} />
      {policy === 'DEFER_AFTER_FY' ? (
        <span className="note" data-testid="limit-cut-defer-note">
          {m.limitCut.deferAfterFyNote}
        </span>
      ) : null}
    </span>
  );
}

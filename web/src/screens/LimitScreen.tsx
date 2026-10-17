// 한도 상세(설계서 §8 2 — 계획 ⑥): 산식(월납 × 배수 → 한도 — 표시 문자열, 계산 없음 — P-10), 누적·잔여·소진, 초년도 기간, 삭감/이연(Q4 문구),
// 계약 상태, 원장 타임라인(history, source_seq 순 그대로). "해약 시 영향"은 Phase 5(비활성).
import { useParams } from 'react-router';
import { ApiError } from '../api/client';
import { useLimit } from '../api/queries';
import { ledgerLabel } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { isZero, present } from '../money/won';
import { dDay } from '../time/dday';
import { dateAt } from '../time/kst';
import { useNow } from '../time/useNow';
import { ComingSoon } from '../ui/EntryPoints';
import { fillNodes } from '../ui/fillNodes';
import { LimitCut } from '../ui/LimitCut';
import { Loading } from '../ui/States';
import { Utilization } from '../ui/Utilization';
import { LedgerBadges } from './LimitsScreen';

export function LimitScreen() {
  const { ledgerId = '' } = useParams();
  const limit = useLimit(ledgerId);
  const nowMs = useNow();
  if (limit.isPending) return <Loading />;
  if (limit.isError) {
    return limit.error instanceof ApiError && limit.error.status === 404 ? <p data-testid="limit-missing">{m.limit.notFound}</p> : null;
  }
  const { ledger, history } = limit.data;
  const label = ledgerLabel();
  return (
    <>
      <h1>{m.limit.title}</h1>
      <section className="card" data-testid="limit-summary">
        <div className="card__head">
          <h2>{ledger.policyMasked}</h2>
          <LedgerBadges ledger={ledger} />
        </div>
        <p data-testid="limit-formula">
          {fillNodes(m.limit.formula, {
            premium: <Amount value={ledger.monthlyPremium} label={label} />,
            multiple: ledger.limitMultiple,
            limit: <Amount value={ledger.limitAmount} label={label} />,
          })}
        </p>
        {ledger.utilization === undefined ? null : <Utilization value={ledger.utilization} />}
        <dl className="rows">
          <div className="row">
            <dt>{m.limit.accumPaid}</dt>
            <dd>
              <Amount value={ledger.accumPaid} label={label} />
            </dd>
          </div>
          <div className="row">
            <dt>{m.limit.remaining}</dt>
            <dd>
              <Amount value={ledger.remaining} label={label} />
            </dd>
          </div>
        </dl>
        <p className="note">
          {fill(m.limit.fy, { start: ledger.fyStart, end: ledger.fyEnd })} · {dDay(ledger.fyEnd, nowMs)}
        </p>
        {isZero(ledger.cutTotal) ? null : <LimitCut amount={ledger.cutTotal} policy="CUT" />}
        {isZero(ledger.deferredTotal) ? null : <LimitCut amount={ledger.deferredTotal} policy="DEFER_AFTER_FY" />}
      </section>
      <section className="card" data-testid="limit-history">
        <h2>{m.limit.history}</h2>
        <ol className="timeline">
          {history.map((h) => (
            <li key={h.sourceSeq} className="timeline__item" data-testid="history-item">
              <p className="timeline__head">
                <span className="badge">{m.enums.historyKind[h.kind]}</span> <span>{dateAt(h.occurredAt)}</span>
                {h.reason === undefined ? null : <span className="note"> {h.reason}</span>}
              </p>
              <dl className="rows">
                {present(h.amount) ? (
                  <div className="row">
                    <dt>{m.limit.amount}</dt>
                    <dd>
                      <Amount value={h.amount} label={label} />
                    </dd>
                  </div>
                ) : null}
                <div className="row">
                  <dt>{m.limit.accumAfter}</dt>
                  <dd>
                    <Amount value={h.accumAfter} label={label} />
                  </dd>
                </div>
                <div className="row">
                  <dt>{m.limit.limitAfter}</dt>
                  <dd>
                    <Amount value={h.limitAfter} label={label} />
                  </dd>
                </div>
              </dl>
            </li>
          ))}
        </ol>
      </section>
      <ComingSoon name={m.limit.impact} testId="entry-impact" />
    </>
  );
}

// 한도 현황(설계서 §8 2 — 계획 ⑥): 필터 주의/전체(화면 필터 — stage 문자열 비교, API status=WARN은 정확히 WARN만이라 쓰지 않는다 — P-4) → 카드.
// 카드: 가린 증권번호 · 체결일 · 소진 바(문자열) · 잔여 · 초년도 종료 D-n · 단계·계약 상태 배지. 상품명·피보험자는 원장 응답에 없다(CR-4).
import { useState } from 'react';
import { Link } from 'react-router';
import { useLimits, type Ledger } from '../api/queries';
import { ledgerLabel } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { dDay } from '../time/dday';
import { useNow } from '../time/useNow';
import { Loading } from '../ui/States';
import { Utilization } from '../ui/Utilization';

type Filter = 'attention' | 'all';

export function LedgerBadges({ ledger }: { ledger: Ledger }) {
  return (
    <span className="badges">
      <span className={ledger.stage === 'OK' ? 'badge' : 'badge badge--warn'} data-testid="stage">
        {m.enums.stage[ledger.stage]}
      </span>
      {ledger.flags.map((f) => (
        <span key={f} className="badge badge--danger" data-testid={`flag-${f}`}>
          {m.enums.flag[f]}
        </span>
      ))}
    </span>
  );
}

function LedgerCard({ ledger, nowMs }: { ledger: Ledger; nowMs: number }) {
  return (
    <article className="card ledger-card" data-testid="ledger-card" data-ledger-id={ledger.ledgerId}>
      <div className="card__head">
        <h2>{ledger.policyMasked}</h2>
        <LedgerBadges ledger={ledger} />
      </div>
      <p className="note">{fill(m.limits.contractDate, { date: ledger.contractDate })}</p>
      {ledger.utilization === undefined ? null : <Utilization value={ledger.utilization} />}
      <dl className="rows">
        <div className="row">
          <dt>{m.limits.remaining}</dt>
          <dd>
            <Amount value={ledger.remaining} label={ledgerLabel()} />
          </dd>
        </div>
      </dl>
      <p className="note">{fill(m.limits.fyEnd, { date: ledger.fyEnd, d: dDay(ledger.fyEnd, nowMs) })}</p>
      <Link className="link-button" to={`/limits/${ledger.ledgerId}`}>
        {m.limits.detail}
      </Link>
    </article>
  );
}

export function LimitsScreen() {
  const limits = useLimits();
  const [filter, setFilter] = useState<Filter>('attention');
  const nowMs = useNow();
  const items = limits.data?.items.filter((l) => filter === 'all' || l.stage !== 'OK') ?? [];
  return (
    <>
      <h1>{m.limits.title}</h1>
      <div className="segmented" role="group" aria-label={m.limits.filter}>
        <button type="button" className="button" aria-pressed={filter === 'attention'} onClick={() => {
            setFilter('attention');
          }} data-testid="filter-attention">
          {m.limits.filterAttention}
        </button>
        <button type="button" className="button" aria-pressed={filter === 'all'} onClick={() => {
            setFilter('all');
          }} data-testid="filter-all">
          {m.limits.filterAll}
        </button>
      </div>
      {limits.isPending ? <Loading /> : null}
      {limits.data !== undefined && items.length === 0 ? <p data-testid="limits-empty">{m.limits.empty}</p> : null}
      {items.map((l) => (
        <LedgerCard key={l.ledgerId} ledger={l} nowMs={nowMs} />
      ))}
    </>
  );
}

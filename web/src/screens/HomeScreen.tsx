// 홈(설계서 §8 — 계획 ⑥): 이번 달 카드(KST 달력 월 — Q16), 주의 계약 N건·납입 지연/실효 M건 → 한도 현황, 비활성 진입점.
// 전월 대비·다음 달 분급 예정액은 보이지 않는다(Q4 — CR-1, Phase 5). 알림은 자리만(Q7).
import { Link } from 'react-router';
import { ApiError } from '../api/client';
import { useLimits, useMonth } from '../api/queries';
import { fill, m } from '../messages';
import { ymAt, ymParts } from '../time/kst';
import { useNow } from '../time/useNow';
import { ComingSoon } from '../ui/EntryPoints';
import { MonthCard } from '../ui/MonthCard';
import { Loading } from '../ui/States';

const ATTENTION_FLAGS = new Set(['OVERDUE', 'LAPSED']);

export function HomeScreen() {
  const nowMs = useNow();
  const ym = ymAt(nowMs);
  const month = useMonth(ym);
  const limits = useLimits();
  const { y, m: mm } = ymParts(ym);
  const heading = fill(m.home.thisMonth, { ym: fill(m.fill.ym, { y, m: mm }) });
  return (
    <>
      <h1 className="visually-hidden">{m.nav.home}</h1>
      {month.isPending ? (
        <Loading />
      ) : month.isError ? (
        month.error instanceof ApiError && month.error.status === 404 ? (
          <section className="card" data-testid="month-empty">
            <h2>{heading}</h2>
            <p>{m.home.noMonth}</p>
            <Link className="link-button" to="/statements">
              {m.home.toStatements}
            </Link>
          </section>
        ) : null
      ) : (
        <>
          <MonthCard month={month.data} heading={heading} nowMs={nowMs} />
          <Link className="link-button" to={`/statements/${ym}`}>
            {m.home.toStatements}
          </Link>
        </>
      )}
      <section className="card" data-testid="attention">
        <h2>{m.home.attention}</h2>
        {limits.data === undefined ? (
          limits.isPending ? <Loading /> : null
        ) : (
          <>
            <p data-testid="attention-limits">{fill(m.home.attentionLimits, { n: String(limits.data.items.filter((l) => l.stage !== 'OK').length) })}</p>
            <p data-testid="attention-flags">
              {fill(m.home.attentionFlags, { n: String(limits.data.items.filter((l) => l.flags.some((f) => ATTENTION_FLAGS.has(f))).length) })}
            </p>
            <Link className="link-button" to="/limits">
              {m.home.toLimits}
            </Link>
          </>
        )}
      </section>
      <section className="card entry-points">
        <h2>{m.home.more}</h2>
        <ComingSoon name={m.nav.timeline} testId="entry-timeline" />
        <ComingSoon name={m.nav.simulation} testId="entry-simulation" />
        <ComingSoon name={m.nav.notifications} testId="entry-notifications" />
      </section>
    </>
  );
}

// 홈(설계서 §8 — 계획 ⑥): 이번 달 카드(KST 달력 월 — Q16), 주의 계약 N건·납입 지연/실효 M건 → 한도 현황, 진입점.
// Phase 5(계획 ⑧): 전월 대비(CR-1 previous — 지난달 값과 그 달 라벨을 나란히, 차감 없음), 다음 달 분급(/me/deferrals 한 칸 — forecastLabel, 없거나 0이면
// "다음 달 예정 없음"), 타임라인·시뮬레이션 진입점이 살아난다. Phase 6(계획 ⑧): 최근 알림 셋 + 안읽음 수(/me/notifications?size=3 한 콜 — 홈 다섯째),
// 없으면 "새 알림이 없습니다", 알림함·문의 내역 진입점.
import { Link } from 'react-router';
import { ApiError } from '../api/client';
import { useDeferrals, useLimits, useMonth, useRecentNotifications, type MonthResponse } from '../api/queries';
import { forecastLabel, monthLabel, netView } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { isZero, present } from '../money/won';
import { ymAt, ymParts, ymShift } from '../time/kst';
import { NotificationItem } from '../notify/NotificationItem';
import { useNow } from '../time/useNow';
import { MonthCard } from '../ui/MonthCard';
import { Loading } from '../ui/States';

const ATTENTION_FLAGS = new Set(['OVERDUE', 'LAPSED']);

const ymText = (ym: string) => fill(m.fill.ym, ymParts(ym));

/** 전월(CR-1): 지난달 세전과 (보이면) 세후를 그 달의 라벨로 — 이번 달과 빼지 않는다. */
function Previous({ month }: { month: MonthResponse }) {
  const prev = month.previous;
  if (prev === undefined) {
    return (
      <p className="note" data-testid="previous-none">
        {m.home.previousNone}
      </p>
    );
  }
  const net = netView(prev.status, prev.netBasis);
  return (
    <dl className="rows" data-testid="previous">
      <div className="row">
        <dt>{fill(m.home.previous, { ym: ymText(prev.closeYm) })}</dt>
        <dd>
          <Amount value={prev.gross} label={monthLabel(prev.status)} />
          {net.kind === 'show' && present(prev.net) ? (
            <>
              {' '}
              <span className="note">{m.home.net}</span> <Amount value={prev.net} label={net.label} />
            </>
          ) : null}
        </dd>
      </div>
    </dl>
  );
}

/** 다음 달 분급(1.1.0 /me/deferrals 한 칸). 월 행이 없거나 0이면 "다음 달 예정 없음". */
function NextMonthDeferral({ ym }: { ym: string }) {
  const deferrals = useDeferrals({ from: ym, to: ym });
  if (deferrals.isPending) return <Loading />;
  if (deferrals.isError) return null;
  const month = deferrals.data.months.find((x) => x.dueYm === ym);
  return (
    <section className="card" data-testid="next-month">
      <h2>{fill(m.home.nextMonth, { ym: ymText(ym) })}</h2>
      {month === undefined || isZero(month.expected) ? (
        <p data-testid="next-month-none">{m.home.nextMonthNone}</p>
      ) : (
        <p data-testid="next-month-amount">
          <Amount value={month.expected} label={forecastLabel()} size="large" />
        </p>
      )}
      <Link className="link-button" to="/deferrals">
        {m.home.toTimeline}
      </Link>
    </section>
  );
}

/** 최근 알림 셋 + 안읽음 수(응답의 unreadCount 그대로). */
function RecentNotifications() {
  const recent = useRecentNotifications();
  return (
    <section className="card" data-testid="recent-notifications">
      <div className="card__head">
        <h2>{m.home.notifications}</h2>
        {recent.data === undefined || recent.data.unreadCount === 0 ? null : (
          <span className="badge" data-testid="unread-count">
            {fill(m.home.unread, { n: String(recent.data.unreadCount) })}
          </span>
        )}
      </div>
      {recent.data === undefined ? (
        recent.isPending ? <Loading /> : null
      ) : recent.data.items.length === 0 ? (
        <p data-testid="notifications-none">{m.home.noNotifications}</p>
      ) : (
        <ul className="notice-list">
          {recent.data.items.map((n) => (
            <NotificationItem key={n.id} notification={n} />
          ))}
        </ul>
      )}
      <Link className="link-button" to="/notifications" data-testid="entry-notifications">
        {m.home.toNotifications}
      </Link>
    </section>
  );
}

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
          <Previous month={month.data} />
          <Link className="link-button" to={`/statements/${ym}`}>
            {m.home.toStatements}
          </Link>
        </>
      )}
      <RecentNotifications />
      <NextMonthDeferral ym={ymShift(ym, 1)} />
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
        <Link className="link-button" to="/deferrals" data-testid="entry-timeline">
          {m.nav.timeline}
        </Link>
        <Link className="link-button" to="/simulations" data-testid="entry-simulation">
          {m.nav.simulation}
        </Link>
        <Link className="link-button" to="/inquiries" data-testid="entry-inquiries">
          {m.nav.inquiries}
        </Link>
      </section>
    </>
  );
}

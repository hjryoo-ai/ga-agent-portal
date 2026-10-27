// 월별 명세(설계서 §8 5 — 계획 ⑥): 월 선택(행이 있는 달만, 36개월) → 월 카드 → 유형별 합계 → 라인 목록(같은 달 접기 — Q6·Q12, 화면 쪽 페이지)
// → 라인 상세. 지급 런 값은 PAID 달에만(지급완료 라벨). 정정(byType.ADJUSTMENT·ADJUSTMENT 라인)은 월 상태와 무관하게 "지급 반영 미정"(A1).
// 예상 이월은 보이지 않는다(Q5).
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { useEarnings, useMonth, useMonthLines, type Line, type MonthResponse, type MonthStatus } from '../api/queries';
import { lineLabel, payoutLabel } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { isZero, present } from '../money/won';
import { ymAt, ymParts, ymShift } from '../time/kst';
import { useNow } from '../time/useNow';
import { LimitCut } from '../ui/LimitCut';
import { isUncertain, MonthCard } from '../ui/MonthCard';
import { Loading } from '../ui/States';
import { foldLines, type Row } from './fold';

const PAGE = 20;

const ymText = (ym: string) => fill(m.fill.ym, ymParts(ym));

function commTypeName(code: string): string {
  const names: Record<string, string> = m.enums.commType;
  return names[code] ?? code;
}

function CrossMonthBadge({ partnerYm: ym, role }: { partnerYm: string | undefined; role: 'original' | 'reversal' }) {
  if (ym === undefined) {
    return (
      <span className="badge badge--danger" data-testid="cross-month">
        {m.statement.crossMonthUnknown}
      </span>
    );
  }
  return (
    <Link className="badge badge--danger badge--link" to={`/statements/${ym}`} data-testid="cross-month">
      {fill(role === 'original' ? m.statement.cancelledTo : m.statement.cancelOf, { ym: ymText(ym) })}
    </Link>
  );
}

function LineItem({ line, status, uncertain, extra }: { line: Line; status: MonthStatus; uncertain: boolean; extra?: ReactNode }) {
  const label = lineLabel(line.commType, status);
  return (
    <div className="line" data-testid="line" data-calc-id={line.calcId}>
      <Link className="line__main" to={`/lines/${line.calcId}`}>
        <span className="line__type">{commTypeName(line.commType)}</span>
        <span className="line__product">{line.productName ?? line.productKey ?? m.line.productUnknown}</span>
        <span className="line__policy">{line.policyMasked}</span>
      </Link>
      <Amount value={line.amount} label={label} uncertain={uncertain} />
      {isZero(line.limitCutAmt) ? null : <LimitCut amount={line.limitCutAmt} policy={line.limitCutPolicy} label={label} uncertain={uncertain} />}
      {extra}
    </div>
  );
}

function RowView({ row, status, uncertain }: { row: Row; status: MonthStatus; uncertain: boolean }) {
  if (row.kind === 'pair') {
    return (
      <div className="line-pair" data-testid="line-pair">
        <LineItem line={row.reversal} status={status} uncertain={uncertain} />
        <details className="line-pair__original">
          <summary>
            <span className="badge">{m.statement.cancelled}</span> {m.statement.showOriginal}
          </summary>
          <LineItem line={row.original} status={status} uncertain={uncertain} />
        </details>
      </div>
    );
  }
  return (
    <LineItem
      line={row.line}
      status={status}
      uncertain={uncertain}
      extra={row.crossMonth === undefined ? null : <CrossMonthBadge partnerYm={row.crossMonth.partnerYm} role={row.crossMonth.role} />}
    />
  );
}

function Lines({ month }: { month: MonthResponse }) {
  const lines = useMonthLines(month.closeYm);
  const [page, setPage] = useState(0);
  if (lines.isPending) return <Loading />;
  if (lines.isError) return null;
  const rows = foldLines(lines.data.items);
  const pages = Math.max(1, Math.ceil(rows.length / PAGE));
  const current = Math.min(page, pages - 1);
  const uncertain = isUncertain(month);
  return (
    <section className="card" data-testid="lines">
      <h2>{fill(m.statement.lines, { n: String(lines.data.total) })}</h2>
      {rows.length === 0 ? <p>{m.statement.noLines}</p> : null}
      {rows.slice(current * PAGE, current * PAGE + PAGE).map((row) => (
        <RowView key={row.kind === 'pair' ? row.reversal.calcId : row.line.calcId} row={row} status={month.status} uncertain={uncertain} />
      ))}
      {pages > 1 ? (
        <div className="pager">
          <button type="button" className="button" disabled={current === 0} onClick={() => {
              setPage(current - 1);
            }}>
            {m.statement.prev}
          </button>
          <span>{fill(m.statement.page, { page: String(current + 1), pages: String(pages) })}</span>
          <button type="button" className="button" disabled={current >= pages - 1} onClick={() => {
              setPage(current + 1);
            }}>
            {m.statement.next}
          </button>
        </div>
      ) : null}
    </section>
  );
}

function MonthBody({ ym, nowMs }: { ym: string; nowMs: number }) {
  const month = useMonth(ym);
  if (month.isPending) return <Loading />;
  if (month.isError) {
    return month.error instanceof ApiError && month.error.status === 404 ? <p data-testid="month-missing">{m.statement.notFound}</p> : null;
  }
  const data = month.data;
  const uncertain = isUncertain(data);
  const types = Object.entries(data.byType).sort(([a], [b]) => a.localeCompare(b));
  return (
    <>
      <MonthCard month={data} heading={ymText(ym)} nowMs={nowMs} />
      {types.length === 0 ? null : (
        <section className="card" data-testid="by-type">
          <h2>{m.statement.byType}</h2>
          <dl className="rows">
            {types.map(([type, amount]) => (
              <div className="row" key={type} data-testid={`by-type-${type}`}>
                <dt>{commTypeName(type)}</dt>
                <dd>
                  <Amount value={amount} label={lineLabel(type, data.status)} uncertain={uncertain} />
                </dd>
              </div>
            ))}
          </dl>
        </section>
      )}
      {data.status === 'PAID' ? (
        <section className="card" data-testid="payout-run">
          <h2>{m.statement.payoutRun}</h2>
          <dl className="rows">
            {(
              [
                ['payable', m.statement.payable, data.payable],
                ['receivableOffset', m.statement.receivableOffset, data.receivableOffset],
                ['carriedReceivable', m.statement.carriedReceivable, data.carriedReceivable],
              ] as const
            ).map(([key, name, value]) =>
              present(value) ? (
                <div className="row" key={key} data-testid={`payout-${key}`}>
                  <dt>{name}</dt>
                  <dd>
                    <Amount value={value} label={payoutLabel()} uncertain={uncertain} />
                  </dd>
                </div>
              ) : null,
            )}
          </dl>
        </section>
      ) : null}
      <Lines key={ym} month={data} />
      <Link className="link-button" to="/inquiries/new" data-testid="entry-inquiry">
        {m.nav.inquiry}
      </Link>
    </>
  );
}

export function StatementScreen() {
  const { ym: param } = useParams();
  const nowMs = useNow();
  const thisYm = ymAt(nowMs);
  const months = useEarnings(ymShift(thisYm, -35), thisYm);
  const available = months.data?.items.map((i) => i.closeYm) ?? [];
  const ym = param ?? available.at(-1);
  return (
    <>
      <h1>{m.statement.title}</h1>
      <nav className="month-picker" aria-label={m.statement.months} data-testid="month-picker">
        {[...available].reverse().map((v) => (
          <Link key={v} className={v === ym ? 'chip chip--current' : 'chip'} to={`/statements/${v}`} aria-current={v === ym ? 'page' : undefined}>
            {ymText(v)}
          </Link>
        ))}
      </nav>
      {months.isPending ? <Loading /> : null}
      {ym === undefined ? months.isPending ? null : <p data-testid="no-months">{m.statement.noMonths}</p> : <MonthBody key={ym} ym={ym} nowMs={nowMs} />}
    </>
  );
}

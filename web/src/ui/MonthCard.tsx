// 월 카드(홈 "이번 달"·월별 명세 머리): 실수령(큰 숫자) · 세전 · 환수 상계 · 원천세 · 정정 · 지급예정일/지급일 · 상태 라벨.
// 세후는 netView 표가 정한다(보이면 그 라벨, 아니면 세전을 크게 + 안내 — Phase 3 수용 심사 Q2). 확인 중(DRIFT·REBUILDING)이면 금액을 흐리게(§6.6).
// 정정만 있는 확정 달은 "정정 N원 — 지급 반영은 정산팀 확인"(Q3 — 단정하지 않는다). 예상 이월 채권은 보이지 않는다(Q5).
import type { ReactNode } from 'react';
import type { MonthResponse } from '../api/queries';
import { monthLabel, netView } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { isZero, present } from '../money/won';
import { dDay } from '../time/dday';
import { dateAt, formatAt } from '../time/kst';
import { fillNodes } from './fillNodes';

export function isUncertain(month: Pick<MonthResponse, 'monthDataStatus'>): boolean {
  switch (month.monthDataStatus) {
    case 'OK':
      return false;
    case 'DRIFT':
    case 'REBUILDING':
      return true;
  }
}

/** 정정만 있는 확정 달(라인 없음 · 정정 0 아님) — Q3. */
export function isAdjustmentOnly(month: Pick<MonthResponse, 'status' | 'byType' | 'adjustment'>): boolean {
  return month.status === 'CONFIRMED' && Object.keys(month.byType).length === 0 && !isZero(month.adjustment);
}

function Row({ name, children, testId }: { name: string; children: ReactNode; testId: string }) {
  return (
    <div className="row" data-testid={testId}>
      <dt>{name}</dt>
      <dd>{children}</dd>
    </div>
  );
}

export function MonthCard({ month, heading, nowMs }: { month: MonthResponse; heading: ReactNode; nowMs: number }) {
  const uncertain = isUncertain(month);
  const status = monthLabel(month.status);
  const net = netView(month.status, month.netBasis);
  const taxLabel = net.kind === 'show' && present(month.net) ? net.label : null;
  return (
    <section className="card month-card" data-testid="month-card" data-status={month.status}>
      <div className="card__head">
        <h2>{heading}</h2>
        <span className="badge" data-testid="month-status">
          {status}
        </span>
        {uncertain ? (
          <span className="badge badge--warn" data-testid="month-drift">
            {m.status.monthData[month.monthDataStatus]}
          </span>
        ) : null}
      </div>
      <div className="month-card__big" data-testid="month-big">
        {net.kind === 'show' && present(month.net) ? (
          <>
            <p className="caption">{m.home.net}</p>
            <Amount value={month.net} label={net.label} size="large" uncertain={uncertain} />
          </>
        ) : (
          <>
            <p className="caption">{m.home.gross}</p>
            <Amount value={month.gross} label={status} size="large" uncertain={uncertain} />
            <p className="note" data-testid="net-note">
              {net.kind === 'hide' ? net.note : m.status.net.unavailable}
            </p>
          </>
        )}
      </div>
      <dl className="rows">
        <Row name={m.home.gross} testId="row-gross">
          <Amount value={month.gross} label={status} uncertain={uncertain} />
        </Row>
        <Row name={m.home.clawbackOffset} testId="row-clawback">
          <Amount value={month.clawbackOffset} label={status} uncertain={uncertain} />
        </Row>
        {taxLabel !== null && present(month.withholdingTax) ? (
          <Row name={m.home.withholdingTax} testId="row-tax">
            <Amount value={month.withholdingTax} label={taxLabel} uncertain={uncertain} />
          </Row>
        ) : null}
        {isZero(month.adjustment) ? null : (
          <Row name={m.home.adjustment} testId="row-adjustment">
            <Amount value={month.adjustment} label={status} uncertain={uncertain} />
          </Row>
        )}
      </dl>
      {month.status === 'ESTIMATED' && month.estimateAsOf !== undefined ? (
        <p className="note" data-testid="estimate-as-of">
          {fill(m.status.estimateAsOf, { at: formatAt(month.estimateAsOf) })}
        </p>
      ) : null}
      {month.status === 'PAID' && month.paidAt !== undefined ? (
        <p className="note" data-testid="paid-on">
          {fill(m.home.paidOn, { date: dateAt(month.paidAt) })}
        </p>
      ) : null}
      {month.status !== 'PAID' && month.payoutExpectedOn !== undefined ? (
        <p className="note" data-testid="payout-expected">
          {fill(m.home.payoutExpected, { date: month.payoutExpectedOn, d: dDay(month.payoutExpectedOn, nowMs) })}
        </p>
      ) : null}
      {isAdjustmentOnly(month) ? (
        <p className="note note--warn" data-testid="adjustment-only">
          {fillNodes(m.adjustmentOnly, { amount: <Amount value={month.adjustment} label={status} uncertain={uncertain} /> })}
        </p>
      ) : null}
    </section>
  );
}

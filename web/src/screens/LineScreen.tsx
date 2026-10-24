// 라인 상세(설계서 §8 5 — 계획 ⑥): 근거(엔진이 준 것만, 없으면 그 줄 생략 — v1.3), 한도 판정(Q4 문구), 계약 요약(가린 증권번호 — 원문은 API에 없다),
// 연결 분급·채권(각 상태 라벨), 원장 링크. 금액 라벨은 그 라인이 속한 달의 상태(월 응답 하나 더 — 라벨 근거), 정정 라인은 "지급 반영 미정"(A1).
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { useLine, useMonth } from '../api/queries';
import { contractLabel, deferralLabel, lineLabel, receivableLabel } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { isZero, present } from '../money/won';
import { ymParts } from '../time/kst';
import { LimitCut } from '../ui/LimitCut';
import { isUncertain } from '../ui/MonthCard';
import { Loading } from '../ui/States';

export function LineScreen() {
  const { calcId = '' } = useParams();
  const detail = useLine(calcId);
  const closeYm = detail.data?.line.closeYm;
  const month = useMonth(closeYm ?? '', closeYm !== undefined);
  if (detail.isPending) return <Loading />;
  if (detail.isError) {
    return detail.error instanceof ApiError && detail.error.status === 404 ? <p data-testid="line-missing">{m.line.notFound}</p> : null;
  }
  if (month.isPending) return <Loading />;
  if (month.isError) return null;
  const { line, basis, policySummary, deferrals, receivable, ledgerId } = detail.data;
  const label = lineLabel(line.commType, month.data.status);
  const uncertain = isUncertain(month.data);
  const names: Record<string, string> = m.enums.commType;
  return (
    <>
      <h1>{m.line.title}</h1>
      <section className="card" data-testid="line-detail">
        <div className="card__head">
          <h2>{names[line.commType] ?? line.commType}</h2>
          <Link className="badge badge--link" to={`/statements/${line.closeYm}`}>
            {fill(m.fill.ym, ymParts(line.closeYm))}
          </Link>
        </div>
        <Amount value={line.amount} label={label} size="large" uncertain={uncertain} />
      </section>
      <section className="card" data-testid="line-basis">
        <h2>{m.line.basis}</h2>
        <dl className="rows">
          {basis.rate === undefined ? null : (
            <div className="row">
              <dt>{m.line.rate}</dt>
              <dd>{basis.rate}</dd>
            </div>
          )}
          {basis.payoutRate === undefined ? null : (
            <div className="row">
              <dt>{m.line.payoutRate}</dt>
              <dd>{basis.payoutRate}</dd>
            </div>
          )}
          {basis.installmentNo === undefined ? null : (
            <div className="row">
              <dt>{m.line.installmentNo}</dt>
              <dd>{fill(m.line.installmentValue, { n: String(basis.installmentNo) })}</dd>
            </div>
          )}
        </dl>
      </section>
      <section className="card" data-testid="line-limit">
        <h2>{m.line.limit}</h2>
        {isZero(line.limitCutAmt) ? <p>{m.line.noCut}</p> : <LimitCut amount={line.limitCutAmt} policy={line.limitCutPolicy} label={label} uncertain={uncertain} />}
        {ledgerId === undefined ? null : (
          <Link className="link-button" to={`/limits/${ledgerId}`}>
            {m.line.toLedger}
          </Link>
        )}
      </section>
      <section className="card" data-testid="line-policy">
        <h2>{m.line.policy}</h2>
        <dl className="rows">
          <div className="row">
            <dt>{m.line.product}</dt>
            <dd>{line.productName ?? line.productKey ?? m.line.productUnknown}</dd>
          </div>
          {line.insuredMasked === undefined ? null : (
            <div className="row">
              <dt>{m.line.insured}</dt>
              <dd>{line.insuredMasked}</dd>
            </div>
          )}
          <div className="row">
            <dt>{m.line.policyNo}</dt>
            <dd data-testid="policy-masked">{line.policyMasked}</dd>
          </div>
          {policySummary.contractDate === undefined ? null : (
            <div className="row">
              <dt>{m.line.contractDate}</dt>
              <dd>{policySummary.contractDate}</dd>
            </div>
          )}
          {present(policySummary.monthlyPremium) ? (
            <div className="row">
              <dt>{m.line.monthlyPremium}</dt>
              <dd>
                <Amount value={policySummary.monthlyPremium} label={contractLabel()} />
              </dd>
            </div>
          ) : null}
        </dl>
      </section>
      {deferrals.length === 0 ? null : (
        <section className="card" data-testid="line-deferrals">
          <h2>{m.line.deferrals}</h2>
          <dl className="rows">
            {deferrals.map((d) => (
              <div className="row" key={d.scheduleId}>
                <dt>{fill(m.fill.ym, ymParts(d.dueYm))}</dt>
                <dd>
                  <Amount value={d.amount} label={deferralLabel(d.status)} />
                </dd>
              </div>
            ))}
          </dl>
        </section>
      )}
      {receivable === undefined ? null : (
        <section className="card" data-testid="line-receivable">
          <h2>{m.line.receivable}</h2>
          <dl className="rows">
            <div className="row">
              <dt>{m.line.receivableAmount}</dt>
              <dd>
                <Amount value={receivable.amount} label={receivableLabel(receivable.status)} />
              </dd>
            </div>
            <div className="row">
              <dt>{m.line.receivableRemaining}</dt>
              <dd>
                <Amount value={receivable.remaining} label={receivableLabel(receivable.status)} />
              </dd>
            </div>
          </dl>
        </section>
      )}
      <Link className="link-button" to={`/inquiries/new?calcId=${encodeURIComponent(calcId)}`} data-testid="entry-inquiry">
        {m.nav.inquiry}
      </Link>
    </>
  );
}

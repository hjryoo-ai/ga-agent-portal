// 분급 타임라인(설계서 §8 — 포털 Phase 5 계획 ⑧): 기간 12/24/48/84개월(이번 달부터) · 계약 하나(한도 카드의 policyKey → 쿼리, 주소 밖) ·
// 유지율 가정(테넌트가 켰을 때만 — features.forecastScenario). 막대 너비는 API의 표시 문자열(bar)을 CSS 사용자 속성으로 그대로 — 계산 없음.
// 막대의 이름은 표의 칸을 가리킨다(aria-labelledby — 금액은 Amount로만 그린다). 라벨: 가정 적용 합계만 retentionLabel, 가정 전·보류·항목은 원값 라벨(Q23).
import { useId, useState, type CSSProperties } from 'react';
import { useDeferrals, useLimits, useMe, type DeferralsResponse } from '../api/queries';
import { deferralLabel, forecastLabel, retentionLabel } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { present } from '../money/won';
import { validRetention } from '../sim/rules';
import { ymAt, ymParts, ymShift } from '../time/kst';
import { useNow } from '../time/useNow';
import { Choices } from '../ui/Choices';
import { policyOptions } from '../ui/policyOptions';
import { Loading } from '../ui/States';

const PERIODS = [12, 24, 48, 84] as const;
type Period = (typeof PERIODS)[number];

const ymText = (ym: string) => fill(m.fill.ym, ymParts(ym));

function Bars({ data, idPrefix }: { data: DeferralsResponse; idPrefix: string }) {
  return (
    <ol className="bars" data-testid="timeline-bars">
      {data.months.map((month) => {
        const id = `${idPrefix}-${month.dueYm}`;
        const style = { '--bar-expected': month.bar?.expected ?? '0.0%', '--bar-held': month.bar?.held ?? '0.0%' } as CSSProperties;
        return (
          <li key={month.dueYm} className="bars__row">
            <span className="bars__ym" aria-hidden="true">
              {ymParts(month.dueYm).m}
            </span>
            <div className="bar" role="img" aria-labelledby={`${id}-m ${id}-e ${id}-h`} data-testid="timeline-bar" style={style}>
              <div className="bar__expected" />
              <div className="bar__held" />
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function Table({ data, idPrefix }: { data: DeferralsResponse; idPrefix: string }) {
  const applied = data.scenario === 'RETENTION' && data.p !== undefined ? retentionLabel(data.p) : forecastLabel();
  const showBase = data.months.some((x) => present(x.expectedBase));
  return (
    <table className="table" data-testid="timeline-table">
      <thead>
        <tr>
          <th scope="col">{m.timeline.month}</th>
          <th scope="col">{m.timeline.expected}</th>
          {showBase ? <th scope="col">{m.timeline.expectedBase}</th> : null}
          <th scope="col">{m.timeline.held}</th>
        </tr>
      </thead>
      <tbody>
        {data.months.map((month) => {
          const id = `${idPrefix}-${month.dueYm}`;
          return (
            <tr key={month.dueYm} data-testid="timeline-month" data-ym={month.dueYm}>
              <th scope="row" id={`${id}-m`}>
                {ymText(month.dueYm)}
              </th>
              <td id={`${id}-e`} data-testid="month-expected">
                <Amount value={month.expected} label={applied} />
              </td>
              {showBase ? (
                <td data-testid="month-expected-base">{present(month.expectedBase) ? <Amount value={month.expectedBase} label={forecastLabel()} /> : null}</td>
              ) : null}
              <td id={`${id}-h`} data-testid="month-held">
                <Amount value={month.held} label={deferralLabel('HELD')} />
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function Items({ data }: { data: DeferralsResponse }) {
  return (
    <section className="card" data-testid="timeline-items">
      <h2>{fill(m.timeline.items, { n: String(data.items.length) })}</h2>
      <ul className="items">
        {data.items.map((item) => (
          <li key={item.scheduleId} className="item" data-testid="timeline-item" data-status={item.status}>
            <p className="item__head">
              <span>{ymText(item.dueYm)}</span> <span className="line__policy">{item.policyMasked}</span>{' '}
              <span className={item.status === 'HELD' || item.status === 'CANCELLED' ? 'badge badge--warn' : 'badge'}>{m.enums.deferral[item.status]}</span>{' '}
              <span className="note">{m.enums.deferralReason[item.reason]}</span>
            </p>
            <Amount value={item.amount} label={deferralLabel(item.status)} />
            <p className="note">{fill(m.timeline.payCondition, { cond: item.payCondition })}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function TimelineScreen() {
  const nowMs = useNow();
  const me = useMe();
  const limits = useLimits();
  const idPrefix = useId();
  const [period, setPeriod] = useState<Period>(12);
  const [policyKey, setPolicyKey] = useState('');
  const [retentionOn, setRetentionOn] = useState(false);
  const [p, setP] = useState('');
  const from = ymAt(nowMs);
  const to = ymShift(from, period - 1);
  const scenarioAvailable = me.data?.features?.forecastScenario === true;
  const retentionWanted = scenarioAvailable && retentionOn;
  const pOk = validRetention(p);
  const deferrals = useDeferrals(
    { from, to, ...(policyKey === '' ? {} : { policyKey }), ...(retentionWanted && pOk ? { retention: p } : {}) },
    !retentionWanted || pOk,
  );
  const options = policyOptions(limits.data?.items ?? []);
  return (
    <>
      <h1>{m.timeline.title}</h1>
      <p className="note note--warn" data-testid="timeline-note">
        {m.timeline.note}
      </p>
      <div className="segmented" role="group" aria-label={m.timeline.period}>
        {PERIODS.map((n) => (
          <button key={n} type="button" className="button" aria-pressed={period === n} data-testid={`period-${String(n)}`} onClick={() => {
              setPeriod(n);
            }}>
            {fill(m.timeline.periodMonths, { n: String(n) })}
          </button>
        ))}
      </div>
      <Choices
        label={m.timeline.policy}
        options={[{ value: '', name: m.timeline.policyAll }, ...options.map((o) => ({ value: o.key, name: o.name }))]}
        value={policyKey}
        onChange={setPolicyKey}
        testId="timeline-policy"
      />
      {scenarioAvailable ? (
        <div className="field" data-testid="timeline-scenario">
          <div className="segmented" role="group" aria-label={m.timeline.scenario}>
            <button type="button" className="button" aria-pressed={!retentionOn} data-testid="scenario-off" onClick={() => {
                setRetentionOn(false);
              }}>
              {m.timeline.scenarioOff}
            </button>
            <button type="button" className="button" aria-pressed={retentionOn} data-testid="scenario-toggle" onClick={() => {
                setRetentionOn(true);
              }}>
              {m.timeline.scenarioOn}
            </button>
          </div>
          {retentionOn ? (
            <label className="field">
              <span>{m.timeline.p}</span>
              <input inputMode="numeric" value={p} data-testid="scenario-p" aria-invalid={!pOk} onChange={(e) => {
                  setP(e.target.value);
                }} />
              {pOk ? null : <span className="field__error" data-testid="scenario-p-error">{m.timeline.pInvalid}</span>}
            </label>
          ) : null}
        </div>
      ) : null}
      <p className="note" data-testid="held-legend">
        {m.timeline.heldLegend}
      </p>
      {retentionWanted && !pOk ? null : deferrals.isPending ? (
        <Loading />
      ) : deferrals.isError ? null : deferrals.data.months.length === 0 ? (
        <p data-testid="timeline-empty">{m.timeline.empty}</p>
      ) : (
        <>
          <section className="card">
            <Bars data={deferrals.data} idPrefix={idPrefix} />
            <Table data={deferrals.data} idPrefix={idPrefix} />
          </section>
          <Items data={deferrals.data} />
        </>
      )}
    </>
  );
}

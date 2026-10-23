// 시뮬레이션(설계서 §8 — 포털 Phase 5 계획 ⑧): 탭 둘(신계약 · 계약 변경·해약 영향). 입력은 문자열 그대로이고 계약에서 생성한 규칙 파일로 미리 막는다(Q12).
// 결과의 모든 금액은 simulationLabel, 고정 문구 "설계사 참고용 · 비확정 · 고객 제시 불가", 룰 스냅샷·요청 번호·요청 시각·(신계약) 엔진 산출 시각.
// 화면은 금액을 계산하지 않는다 — 소진율 없음(한도액과 예상 누적을 나란히), 해약 영향은 변경 전/후 두 값(차이 없음 — Q24, "실수령"이라 쓰지 않는다).
// 마지막 결과는 이 화면의 메모리에만 두고, 429·503·502·422에도 그대로 보인다. 엔진 재시도는 없다(변이 retry false).
import { useState, type KeyboardEvent, type SyntheticEvent, type ReactNode } from 'react';
import {
  SimulationError,
  useLimits,
  useNewContractSimulation,
  usePolicyEventSimulation,
  type SimNewContractResponse,
  type SimPolicyEventResponse,
} from '../api/queries';
import { simulationLabel } from '../labels/labels';
import { fill, m } from '../messages';
import { Amount } from '../money/Amount';
import { present } from '../money/won';
import { body, check, type FieldProblem, type RequestName } from '../sim/rules';
import { EVENT_TYPES } from '../sim/eventTypes';
import { takePolicy } from '../sim/handoff';
import { dateAt, formatAt, ymParts } from '../time/kst';
import { useNow } from '../time/useNow';
import { policyOptions } from '../ui/policyOptions';

type Tab = 'new' | 'event';

const ymText = (ym: string) => fill(m.fill.ym, ymParts(ym));

/** 오류 → 문구(포털 고정 바이트의 code와 상태). 429는 Retry-After 문자열 그대로. */
export function errorText(error: Error): string {
  if (!(error instanceof SimulationError)) return m.simulation.failed;
  switch (error.status) {
    case 429:
      return fill(m.simulation.rateLimited, { n: error.retryAfter ?? '' });
    case 503:
      return m.simulation.unavailable;
    case 502:
      return m.simulation.engineContract;
    case 404:
      return m.simulation.notFound;
    case 400:
      return m.simulation.invalidRequest;
    case 422:
      switch (error.code) {
        case 'INSURER_AMBIGUOUS':
          return m.simulation.insurerNeeded;
        case 'UNSUPPORTED_EVENT_TYPE':
          return m.simulation.unsupportedEvent;
        default:
          return m.simulation.engineRejected;
      }
    default:
      return m.simulation.failed;
  }
}

function Field({ name, label, problem, children }: { name: string; label: string; problem: FieldProblem | undefined; children: ReactNode }) {
  return (
    <label className="field" data-testid={`field-${name}`}>
      <span>{label}</span>
      {children}
      {problem === undefined ? null : (
        <span className="field__error" data-testid={`error-${name}`}>
          {fill(problem === 'required' ? m.simulation.required : m.simulation.invalid, { field: label })}
        </span>
      )}
    </label>
  );
}

function Facts({ result, generatedAt }: { result: { simId: string; requestedAt: string; ruleSnapshotId: string }; generatedAt?: string }) {
  return (
    <div className="sim-facts" data-testid="sim-facts">
      <p className="note" data-testid="sim-rule-snapshot">
        {fill(m.simulation.ruleSnapshot, { id: result.ruleSnapshotId })}
      </p>
      <p className="note" data-testid="sim-id">
        {fill(m.simulation.simId, { id: result.simId })}
      </p>
      <p className="note" data-testid="sim-requested-at">
        {fill(m.simulation.requestedAt, { at: formatAt(result.requestedAt) })}
      </p>
      {generatedAt === undefined ? null : (
        <p className="note" data-testid="sim-generated-at">
          {fill(m.simulation.generatedAt, { at: formatAt(generatedAt) })}
        </p>
      )}
    </div>
  );
}

function Row({ name, children, testId }: { name: string; children: ReactNode; testId: string }) {
  return (
    <div className="row" data-testid={testId}>
      <dt>{name}</dt>
      <dd>{children}</dd>
    </div>
  );
}

export function NewContractResult({ result }: { result: SimNewContractResponse }) {
  const label = simulationLabel();
  return (
    <section className="card" data-testid="sim-result-new">
      <h2>{m.simulation.lastResult}</h2>
      <p className="note note--warn" data-testid="sim-note">
        {m.simulation.note}
      </p>
      <h3>{m.simulation.byInstallment}</h3>
      <table className="table" data-testid="sim-installments">
        <tbody>
          {result.fyCommissionByInstallment.map((i) => (
            <tr key={i.no}>
              <th scope="row">{fill(m.simulation.installment, { n: String(i.no) })}</th>
              <td>
                <Amount value={i.amount} label={label} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <dl className="rows" data-testid="sim-limit">
        {present(result.limitAmount) ? (
          <Row name={m.simulation.limitAmount} testId="sim-limit-amount">
            <Amount value={result.limitAmount} label={label} />
          </Row>
        ) : null}
        {present(result.projectedAccum) ? (
          <Row name={m.simulation.projectedAccum} testId="sim-projected-accum">
            <Amount value={result.projectedAccum} label={label} />
          </Row>
        ) : null}
        {present(result.limitAmount) || present(result.projectedAccum) ? null : <p className="note">{m.simulation.noLimit}</p>}
      </dl>
      <h3>{m.simulation.incentives}</h3>
      <ul className="items" data-testid="sim-incentives">
        {result.incentives.map((i) => (
          <li key={i.code} className="item" data-testid="sim-incentive">
            <span>{i.code}</span> <Amount value={i.amount} label={label} />{' '}
            <span className="badge">{i.limitIncluded ? m.simulation.incentiveLimit : m.simulation.incentiveNoLimit}</span>
            {present(i.limitCutAmt) ? (
              <span className="note"> {m.limitCut.label} <Amount value={i.limitCutAmt} label={label} /></span>
            ) : null}
          </li>
        ))}
      </ul>
      <dl className="rows">
        <Row name={m.simulation.firstYearPayout} testId="sim-first-year">
          <Amount value={result.deferral.firstYearPayout} label={label} />
        </Row>
      </dl>
      {result.deferral.schedule.length === 0 ? null : (
        <>
          <h3>{m.simulation.schedule}</h3>
          <table className="table" data-testid="sim-schedule">
            <tbody>
              {result.deferral.schedule.map((d) => (
                <tr key={d.dueYm}>
                  <th scope="row">{ymText(d.dueYm)}</th>
                  <td>
                    <Amount value={d.amount} label={label} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      <Facts result={result} generatedAt={result.generatedAt} />
    </section>
  );
}

export function PolicyEventResult({ result }: { result: SimPolicyEventResponse }) {
  const label = simulationLabel();
  const o = result.offsetAgainstMonth;
  return (
    <section className="card" data-testid="sim-result-event">
      <h2>{m.simulation.lastResult}</h2>
      <p className="note note--warn" data-testid="sim-note">
        {m.simulation.note}
      </p>
      <dl className="rows">
        <Row name={m.simulation.clawback} testId="sim-clawback">
          <Amount value={result.clawbackAmount} label={label} />
        </Row>
      </dl>
      <h3 data-testid="sim-offset-title">{fill(m.simulation.offset, { ym: ymText(o.closeYm) })}</h3>
      <dl className="rows rows--pair" data-testid="sim-offset">
        <Row name={m.simulation.before} testId="sim-offset-before">
          <Amount value={o.before} label={label} />
        </Row>
        <Row name={m.simulation.after} testId="sim-offset-after">
          <Amount value={o.after} label={label} />
        </Row>
      </dl>
      <h3>{m.simulation.cancelled}</h3>
      {result.cancelledDeferrals.length === 0 ? (
        <p className="note">{m.simulation.noCancelled}</p>
      ) : (
        <table className="table" data-testid="sim-cancelled">
          <tbody>
            {result.cancelledDeferrals.map((d) => (
              <tr key={d.scheduleId}>
                <th scope="row">{ymText(d.dueYm)}</th>
                <td>
                  <Amount value={d.amount} label={label} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <dl className="rows">
        <Row name={m.simulation.receivableRemaining} testId="sim-receivable">
          <Amount value={result.receivableRemaining} label={label} />
        </Row>
      </dl>
      <Facts result={result} />
    </section>
  );
}

function ErrorNote({ error }: { error: Error | null }) {
  if (error === null) return null;
  return (
    <p className="note note--danger" role="alert" data-testid="sim-error" data-status={error instanceof SimulationError ? String(error.status) : ''}>
      {errorText(error)}
    </p>
  );
}

function useForm(name: RequestName, initial: Record<string, string>) {
  const [values, setValues] = useState(initial);
  const [shown, setShown] = useState<Record<string, FieldProblem>>({});
  const set = (field: string) => (value: string) => {
    setValues((v) => ({ ...v, [field]: value }));
  };
  /** 보내기 전 검사 — 문제가 있으면 보이고 false. */
  const validate = (): boolean => {
    const problems = check(name, values);
    setShown(problems);
    return Object.keys(problems).length === 0;
  };
  return { values, set, problems: shown, validate, body: () => body(name, values) };
}

function NewContractForm({ today }: { today: string }) {
  const limits = useLimits();
  const sim = useNewContractSimulation();
  const [last, setLast] = useState<SimNewContractResponse | null>(null);
  const [needInsurer, setNeedInsurer] = useState(false);
  const form = useForm('SimNewContractRequest', { productKey: '', monthlyPremium: '', contractDate: today, paymentTermMonths: '', insurerCd: '' });
  const products = [...new Map((limits.data?.items ?? []).flatMap((l) => (l.productKey === undefined ? [] : [[l.productKey, l.productName ?? l.productKey] as const]))).entries()];
  const submit = (e: SyntheticEvent) => {
    e.preventDefault();
    if (!form.validate()) return;
    sim.mutate(form.body() as Parameters<typeof sim.mutate>[0], {
      onSuccess: setLast,
      onError: (error) => {
        if (error instanceof SimulationError && error.code === 'INSURER_AMBIGUOUS') setNeedInsurer(true);
      },
    });
  };
  return (
    <>
      <form className="form" noValidate onSubmit={submit} data-testid="form-new">
        <Field name="productKey" label={m.simulation.productKey} problem={form.problems['productKey']}>
          <input list="sim-products" value={form.values['productKey']} onChange={(e) => {
              form.set('productKey')(e.target.value);
            }} aria-describedby="sim-product-hint" />
          <datalist id="sim-products">
            {products.map(([key, name]) => (
              <option key={key} value={key}>
                {name}
              </option>
            ))}
          </datalist>
          <span className="note" id="sim-product-hint">
            {m.simulation.productHint}
          </span>
        </Field>
        <Field name="monthlyPremium" label={m.simulation.monthlyPremium} problem={form.problems['monthlyPremium']}>
          <input inputMode="numeric" value={form.values['monthlyPremium']} onChange={(e) => {
              form.set('monthlyPremium')(e.target.value);
            }} />
        </Field>
        <Field name="contractDate" label={m.simulation.contractDate} problem={form.problems['contractDate']}>
          <input type="date" value={form.values['contractDate']} onChange={(e) => {
              form.set('contractDate')(e.target.value);
            }} />
        </Field>
        <Field name="paymentTermMonths" label={m.simulation.paymentTermMonths} problem={form.problems['paymentTermMonths']}>
          <input inputMode="numeric" value={form.values['paymentTermMonths']} onChange={(e) => {
              form.set('paymentTermMonths')(e.target.value);
            }} />
        </Field>
        {needInsurer ? (
          <Field name="insurerCd" label={m.simulation.insurerCd} problem={form.problems['insurerCd']}>
            <input value={form.values['insurerCd']} onChange={(e) => {
                form.set('insurerCd')(e.target.value);
              }} />
          </Field>
        ) : null}
        <button type="submit" className="button button--primary" disabled={sim.isPending} data-testid="run-new">
          {sim.isPending ? m.simulation.running : m.simulation.run}
        </button>
      </form>
      <ErrorNote error={sim.error} />
      {last === null ? null : <NewContractResult result={last} />}
    </>
  );
}

function PolicyEventForm({ today, handedOff }: { today: string; handedOff: string | null }) {
  const limits = useLimits();
  const sim = usePolicyEventSimulation();
  const [last, setLast] = useState<SimPolicyEventResponse | null>(null);
  const form = useForm('SimPolicyEventRequest', {
    policyKey: handedOff ?? '',
    eventType: 'CANCEL',
    asOfDate: today,
    elapsedInstallments: '',
    newMonthlyPremium: '',
  });
  const options = policyOptions(limits.data?.items ?? []);
  const reduce = form.values['eventType'] === 'REDUCE';
  const submit = (e: SyntheticEvent) => {
    e.preventDefault();
    if (!form.validate()) return;
    const values = form.body();
    // 감액이 아니면 감액 뒤 월납을 보내지 않는다(입력란이 숨겨져 있다)
    const sent = reduce ? values : Object.fromEntries(Object.entries(values).filter(([k]) => k !== 'newMonthlyPremium'));
    sim.mutate(sent as Parameters<typeof sim.mutate>[0], { onSuccess: setLast });
  };
  return (
    <>
      <form className="form" noValidate onSubmit={submit} data-testid="form-event">
        <Field name="policyKey" label={m.simulation.policy} problem={form.problems['policyKey']}>
          <select value={form.values['policyKey']} onChange={(e) => {
              form.set('policyKey')(e.target.value);
            }}>
            <option value="">{m.simulation.policyChoose}</option>
            {options.map((o) => (
              <option key={o.key} value={o.key}>
                {o.name}
              </option>
            ))}
          </select>
        </Field>
        <Field name="eventType" label={m.simulation.eventType} problem={form.problems['eventType']}>
          <select value={form.values['eventType']} onChange={(e) => {
              form.set('eventType')(e.target.value);
            }}>
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {m.enums.eventType[t]}
              </option>
            ))}
          </select>
        </Field>
        <Field name="asOfDate" label={m.simulation.asOfDate} problem={form.problems['asOfDate']}>
          <input type="date" value={form.values['asOfDate']} onChange={(e) => {
              form.set('asOfDate')(e.target.value);
            }} />
        </Field>
        <Field name="elapsedInstallments" label={m.simulation.elapsedInstallments} problem={form.problems['elapsedInstallments']}>
          <input inputMode="numeric" value={form.values['elapsedInstallments']} onChange={(e) => {
              form.set('elapsedInstallments')(e.target.value);
            }} />
        </Field>
        {reduce ? (
          <Field name="newMonthlyPremium" label={m.simulation.newMonthlyPremium} problem={form.problems['newMonthlyPremium']}>
            <input inputMode="numeric" value={form.values['newMonthlyPremium']} onChange={(e) => {
                form.set('newMonthlyPremium')(e.target.value);
              }} />
          </Field>
        ) : null}
        <button type="submit" className="button button--primary" disabled={sim.isPending} data-testid="run-event">
          {sim.isPending ? m.simulation.running : m.simulation.run}
        </button>
      </form>
      <ErrorNote error={sim.error} />
      {last === null ? null : <PolicyEventResult result={last} />}
    </>
  );
}

const TABS: readonly Tab[] = ['new', 'event'];

export function SimulationScreen() {
  const nowMs = useNow();
  const [handedOff] = useState(takePolicy);
  const [tab, setTab] = useState<Tab>(handedOff === null ? 'new' : 'event');
  const today = dateAt(new Date(nowMs).toISOString());
  const names: Record<Tab, string> = { new: m.simulation.tabNewContract, event: m.simulation.tabPolicyEvent };
  // 탭 목록 키보드(WAI-ARIA 탭): 좌우 화살표로 옮기고 포커스도 옮긴다
  const onKey = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    e.preventDefault();
    const next: Tab = tab === 'new' ? 'event' : 'new';
    setTab(next);
    document.getElementById(`sim-tab-${next}`)?.focus();
  };
  return (
    <>
      <h1>{m.simulation.title}</h1>
      <p className="note note--warn" data-testid="simulation-note">
        {m.simulation.note}
      </p>
      <div className="tabs" role="tablist" aria-label={m.simulation.tabs}>
        {TABS.map((t) => (
          <button
            key={t}
            id={`sim-tab-${t}`}
            type="button"
            role="tab"
            className="tab"
            aria-selected={tab === t}
            aria-controls={`sim-panel-${t}`}
            tabIndex={tab === t ? 0 : -1}
            data-testid={`tab-${t}`}
            onClick={() => {
              setTab(t);
            }}
            onKeyDown={onKey}
          >
            {names[t]}
          </button>
        ))}
      </div>
      {/* 두 패널은 늘 그려 두고 숨긴다 — 탭을 오가도 입력·마지막 결과가 남는다 */}
      <div id="sim-panel-new" role="tabpanel" aria-labelledby="sim-tab-new" hidden={tab !== 'new'}>
        <NewContractForm today={today} />
      </div>
      <div id="sim-panel-event" role="tabpanel" aria-labelledby="sim-tab-event" hidden={tab !== 'event'}>
        <PolicyEventForm today={today} handedOff={handedOff} />
      </div>
    </>
  );
}

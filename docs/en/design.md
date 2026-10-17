> 🌐 [한국어](../설계서.md) · **English** — translation of `docs/설계서.md` at commit `2649700`. The Korean original is canonical; where the two differ, the Korean text prevails.

# GA Agent "My Commissions & Limits" Dashboard Design Document (v1.6)

> Change history — v1.6 (2026-10-17, portal Phase 3 query API, authentication and authorization — directive `docs/phase-03-지시문.md`, plan `docs/phase-03-계획.md`, approval `docs/phase-03-계획승인.md`): §5 V5 (`tenant.oidc_issuer`·`oidc_audience`, `identity_link.role`·`status`·`org_path`·`linked_at`, `agent_id` NULL only for `OPERATOR`, `ingest_cursor.caught_up_at`, `audit_access.detail`, `policy_limit.limit_multiple` → `TEXT` — `trim_scale(…)::text`, **DE-18**), `tenant.params.sync_delay_seconds` (step 2). §9 tenant = token `iss` (dedicated role `portal_issuer` — **DE-12**), verification order with eight reasons and byte-identical 401, `Caller` only from `identity_link`, byte-identical 404 and `AUTHZ_DENIED` audit (separate transaction; 401 only as a metric — **DE-17**), two `org_path` sources (**DE-13**), first entry of the direct-access allowlist (step 3), query API rules, second SQL predicate, canonical authorization matrix (step 5). §7 endpoints, two status fields `dataStatus` (sync — based on `caught_up_at`, **DE-14**) and `monthDataStatus` (reconciliation) (**DD-12**), own name as is (**DD-13**), no policy number — `policyMasked`·`policyKey` HMAC (**DE-15**), 400·401·404, weak ETag `seq.sync.cfg` (**DE-16**), contract `contracts/api/v1/portal-api.openapi.yaml` (step 4). §6.1 operator command authorization (`OPERATOR` link, `--actor` retired — step 6). §10 query metrics, statement count for the three home calls. §13 authorization and isolation rows. §14.1 #2·#5·#6·#9 (step 8).
>
> v1.5.1 (2026-10-17): **Portal contract v1.4** (Phase 2 acceptance review `docs/phase-02-수용심사.md` §3 N1–N6 — pre-approved by the review, Phase 3 step 0) — the canonical Appendix A-1 is replaced by the **second edition** from engine E2 ⓪ (`a1_portal_150000_e2.jsonl` — the first edition's 10 events + 202702 `StatementEstimated`, 11 events); `contract-only/07` is replaced by the real sample at seq 11 of that stream (file deleted); the engine's five provisional-statement streams become test resources (not contract). §4.3: key `'no'` (a YAML 1.1 parser read it as a boolean — **DD-11**), sign of `clawbackAmount` (positive for reinstatement), definitions of the simulation response fields, the meaning of `INSURER_AMBIGUOUS`. `AgentSnapshot.status` as a closed enumeration `ACTIVE`/`DISMISSED` (narrowing — engine `e2.1` moves `UPSTREAM` to this merge commit).
>
> v1.5 (2026-10-17, portal Phase 2 projections — directive `docs/phase-02-지시문.md`, plan `docs/phase-02-계획.md`, approval `docs/phase-02-계획승인.md`): §5 V4 (`agent_profile.role` NULL and closed values for `status`, four payout-confirmed columns of `earnings_month`, `estimate_basis_seq`, `net_basis`, `policy_limit_history` key = event `seq` (**DD-5**), `policy_flag.reason`, closed vocabularies for receivables and deferrals, `recon_result.source`·`source_seq`), 19 closed-value columns, `data_status = REBUILDING` unused (Q9), invariant sentence (**DD-8** — "`accum_paid ≤ limit_amount`", which contradicted reduced recalculation, is now an engine rule). §4.2 "effect" column (4 immutable-fact types `DUPLICATE_KEY`, 3 state-fact types upsert — Q4), §6.2 after-tax transition table, `net_basis` (D-2·Q1), deferral diagram (**DD-9**), §6.3 E-2 (step 3), §6.4 HELD·`LIMIT_DEFER` (step 7). §6.1 operator command `ingest rebuild`, §6.6 event reconciliation as the first point (D-3), **§6.7 shadow schema → three-transaction replay** (D-11·Q10 — **DE-9**), §10 metrics `projection_drift_total`·`ingest_rebuild_seconds`, §14.1 9–12 (steps 5 and 7).
>
> v1.4.2 (2026-10-17): **Portal contract v1.3** (Phase 2 plan approval `docs/phase-02-계획승인.md` §3) — Appendix A-1 is replaced by the stream produced by engine E2 ⓪a (`samples/a1/a1_portal_150000.jsonl`, byte-identical to engine `a124621`) (D-1·Q13). §4.3 synchronous API gets requests R1–R8 from the engine E2 plan §4 (insurer key, elapsed installment count and new premium as optional inputs, result without limit applied, incentive amount definition and cut amount, meaning of result fields, 400·404·422, meaning of a CONFIRMED statement) — all additions, relaxations and clarifications, `version` stays 1. **DD-10** (§4.3 omitted engine inputs).
>
> v1.4.1 (2026-10-17): **Portal contract v1.2** (portal Phase 2 directive `docs/phase-02-지시문.md` §0 M1–M5, Phase 1 acceptance review `docs/phase-01-수용심사.md` §3-3·4) — feed `400 INVALID_REQUEST` (addition), `DeferralStatusChanged.reason` and `ClawbackReceivableUpdated.status` as closed enumerations of the engine vocabulary, receivable `amount`·`remaining` 0 or more (**narrowing** — the engine moves `UPSTREAM` to this merge commit in E2 step 0), sample fixes (08·13·14·16) and directory split (`samples/a1/`·`contract-only/`·`standalone/`). Tables in §4.1·§4.2·§4.3. §5 meaning of `tenant.status` (acceptance review Q1 — **DD-6**). Appendix A-1 header (canonical values are the engine E2 step 0 stream — **DD-7**). The `policy_limit_history` key (**DD-5**) comes in Phase 2 V4.
>
> v1.4 (2026-10-17, portal Phase 1 ingest — directive `docs/phase-01-지시문.md`, plan `docs/phase-01-계획.md`, approval `docs/phase-01-계획승인.md`): §5 V3 (`ingest_event.payload_hash`, `SKIPPED` unused), §9 role `portal_directory`. §6.1 ingest conventions (step 4 — `APPLY_ERROR` is one reason not in the plan), schedule and `tenant.status` condition (step 5), operator commands (step 6 — `INGEST_PAUSE_REJECTED` is one audit action not in the plan's list), §10 metric names, §4.1 engine-side DDL sketch replaced by a reference to engine design document §6.8 and the feed conventions (step 7). 
>
> v1.3.1 (2026-10-17): **Portal contract v1.1.1** (Phase 1 plan approval `docs/phase-01-계획승인.md` §3 L1–L3 — facts the engine E1 plan confirmed in code): `LimitLedgerRebased.postingSeq` optional (a recalculation is not a posting), `PayoutCompleted.paidAt` is the payout confirmation time (not transfer execution), `limitCutPolicy` is the rule value as is, and the current engine treats `DEFER_AFTER_FY` the same as a cut. Appendix A-1 header and seq 6 comment. §4.2.
>
> v1.3 (2026-10-17): **Portal contract v1.1** (portal Phase 1 directive `docs/phase-01-지시문.md` §0 K1–K9) — places that required as mandatory data the engine (`ga-commission-engine` `85b4c71`) does not have are relaxed to optional (K1–K4), the payout run's confirmed values are added as optional `PayoutCompleted` fields (K5), amount definitions are written as the same sentences in the contract description and the §5 comments (K6·K7), and the feed response gains a mandatory `tenantId`, 422 `AFTER_BEYOND_HEAD` and a `limit` default of 200 (K8). All are relaxations and additions, so the event `version` stays 1 (§4.5). **DD-4** (design document defect — mandatory fields were decided without checking against the engine schema). §4·§4.1·§4.2·§4.3·§4.4·§5·§6.2·§8·§9·§12·§14.0.
>
> v1.2 (2026-10-16): Reflects portal Phase 0 directive v1.2 — plan `docs/phase-00-계획.md` S1–S13, plan approval `docs/phase-00-계획승인.md` Q1–Q9·I1. Revised on the premise of the platform artifact published by ga-disclosure (`com.ga.platform` 0.2.0), the shared Envelope core and the working conventions (§3.3·§4·§5·§9·§11·§13). **Q1 is a defect (contradiction) in design document v1.0·v1.1** — the `UNIQUE (tenant_id, dedup_key)` of §5 `notification` conflicted with the per-agent keys of §6.5 (`STMT_CONFIRMED:{ym}`·`PAYOUT:{ym}`). **I1 is something the §4.2 table did not define** — `CommissionCalculated.amount` is the payout portion after the limit cut. The acceptance review (`docs/phase-00-수용심사.md`) added **DD-3** — §5 `simulation_log.status` had no value set (set to `REQUESTED`/`SUCCEEDED`/`FAILED`) — and one line on data migration in the §5 RLS paragraph (B1). The body is revised at each Phase 0 step in the same commit as that step's code.
>
> v1.1 (2026-09-22): Reflects confirmation of 4 open questions (§14.0). Org manager's team-member line exposure = tenant flag, rate exposure = line basis figures, provisional statement = once nightly (cycle is a parameter), retention-rate scenario = provided by default, disabled by default.

> Purpose: the top-level design of an agent-facing portal that lets field agents of a GA check for themselves **"How much do I get this month · how much limit is left on this policy · how much will come in each month from now on · how much do I pay back if this is surrendered · how much do I get if I write this policy"**.
> This document is the canonical document for instructing Claude Code to implement, and implementation follows the roadmap order of section 12.
> Premise: calculation, payout, clawback and settlement of commissions are handled by the existing `ga-commission-engine` (Java/Spring/Oracle). This system is a **read-only portal that restructures and displays those results from the agent's perspective**.
>
> **Disclaimer**: a document for learning and portfolio purposes. Regulatory content (the 1200% rule, the deferral scheme, the comparison-and-explanation duty, etc.) is an illustrative summary based on public sources; the actual applicable standards follow the original texts and authoritative interpretations of the Financial Services Commission (금융위원회) and the Financial Supervisory Service (금융감독원). All rates, amounts and thresholds are fictional example values.

---

## 0. Overarching Premises of the Design

1. **The engine computes the money; the portal displays it.** The portal does not itself derive any commission, clawback or tax amount. The only permitted operations are **summation and ratio display** of integer (won) amounts the engine has confirmed. After-tax net take-home amounts and simulation results are always obtained from the engine.
2. **Never mix states.** The core of agent trust is not the number itself but that the screen never blurs whether that number is **estimated (provisional), confirmed (closed), or paid**. Every amount carries a status next to it.
3. **The read model must be rebuildable from scratch at any time.** The portal DB is a derivative of the engine event stream. Replaying from event seq 0 reproduces the same screens (determinism). This is the only recovery means for failures, corrections and suspicious situations.
4. **Tenant isolation is enforced by structure.** This solution is supplied to multiple GAs (such as insurer-subsidiary GAs). Data access without a `tenant_id` condition must fail at the test and startup stages, and is blocked once more at the DB level (RLS).
5. **On an agent's screen, only the agent's own data, and only as much as needed.** Other people's commissions, internal-policy rate tables and customer identification information do not appear on screen. Simulation is for the agent's internal reference and is not material to be presented to customers (§2.3).

---

## 1. Problem Definition and Users

### 1.1 Why this screen is needed now

| Environmental change | Question the agent now has | How the answer is obtained today |
|---|---|---|
| From 2026-07-01 the 1200% rule extends from GA to per-agent payouts (incentives and settling-in support payments included in the sum) | "Can I still get more incentives from this policy, how much limit is left?" | Ask the settlement team, or don't know |
| 2027-01 deferral scheme scheduled to take effect (first-year commission paid in installments over several years; final notice of the supervision regulations needs to be checked) | "From next year, how much will actually come in each month?" | Estimate on their own in Excel |
| Offset of clawbacks (surrender, lapse, withdrawal) | "If this policy is surrendered, how much will be deducted from this month's payout?" | Only known after receiving the payout statement |
| 13-month agent retention rate of large GAs 57.8% (as of end of 2024, press reports), increasing agent turnover | "Am I better off staying at this GA?" — unpredictable income fuels attrition | None |

The settlement system (the engine) **already has all of this information** (limit ledger, deferral schedule, clawback receivables, closing statements). The only problem is that agents cannot see it. Therefore the essence of this system is not "new calculation" but **projection of the engine ledger from the agent's perspective**.

### 1.2 Users and scope of permissions

| User | v1 scope | What they can see |
|---|---|---|
| Agent (self) | **Core** | Own commissions, limits, deferrals, clawbacks, statements, notifications, simulations |
| Org manager (team lead / branch manager / division head) | v2 | Own data + subordinate organization totals + own override lines. Individual team-member lines are an internal-policy parameter |
| Settlement staff | v2 (support console) | Handling disputes, reconciliation results, ingest status, running rebuilds |
| System operator | v1 (CLI / admin API) | Tenant registration, ingest cursor, rebuild |

### 1.3 The agent's five questions (Jobs-to-be-done) → screens

| # | Question | Screen | Data source |
|---|---|---|---|
| Q1 | How much comes in this month (pre-tax/after-tax, when) | Home | `earnings_month` + engine statement snapshot |
| Q2 | How much limit is left on this policy, why was it cut | Limit status | `policy_limit` + history |
| Q3 | How much will come in each month from now on | Deferral timeline | `deferral_item` |
| Q4 | How much do I pay back if this policy is surrendered | Simulation (surrender impact) | Engine synchronous API |
| Q5 | How much do I get if I write this policy | Simulation (new policy) | Engine synchronous API |

### 1.4 How the business model affects the design

This solution is part of an in-house GA operations IT solution and is **supplied to multiple GAs such as insurer-subsidiary GAs**. Therefore:

- The portal is **multi-tenant**. One deployment serves multiple GAs, but data is fully isolated.
- The commission engine must be a **replaceable source**. Our engine is the reference implementation, but a GA with its own settlement system can use the portal as is by matching the same event contract (§4) with an adapter. This is why the portal and the engine are coupled **only by contract**.
- It is likely to be **embedded as a webview** inside the adopting GA's existing agent app, so it is built as a mobile-first web app.

---

## 2. Scope

### 2.1 Included in v1

- 5 screens (§8): home, limit status, deferral timeline, simulation (new policy / surrender impact), monthly statement (line drill-down)
- Notification inbox (in-app) + notification rules (§6.5), disputes (lightweight: line-level inquiry submission and status)
- Engine integration contract v1 (outbox event feed + 4 synchronous APIs) and engine-side changes (§4.4)
- Multi-tenancy, authentication, authorization, audit log, daily reconciliation (Recon), rebuild, demo tenant data generator

### 2.2 Excluded from v1 (later)

Org manager view, PDF payout statement, push / KakaoTalk notification (알림톡) channels, offline snapshot cache, renewal commission outlook (the engine has no schedule, so it cannot be estimated without rules), insurance product comparison-and-explanation confirmation (a separate second-priority feature), settlement staff console UI.

### 2.3 Compliance boundary (design constraint)

- Simulation results are **for the agent's internal reference**. The screen carries a fixed "not confirmed · not to be presented to customers" notice, and every simulation request and response is recorded in `simulation_log` (including the rule snapshot ID; reproducible).
- Large GAs' comparison-and-explanation duty (comparing 3 or more products of the same or similar kind, explaining sales commission grades and rankings) is a customer-facing procedure and outside the scope of this system. This system contributes by **letting agents know their own limits and commissions accurately**, thereby reducing irregular payout requests and attempts to exceed limits in advance.
- The rate information an agent sees is limited to **the actual figures of the basis of their own calculation lines (applied rate, payout rate, installment number)** (confirmed, §14.0). The full internal-policy rate table, other people's grades and payout rates, and rates of other products are not exposed — the line basis goes only as far as answering "why is this line this amount".

---

## 3. System Architecture

### 3.1 Architecture decisions (Decision Record)

| ID | Decision | Rationale | Rejected alternative |
|---|---|---|---|
| D-1 | **Separate repository, separate deployment** (`ga-agent-portal`). Coupled with the engine only through the §4 contract | Users (agents vs settlement team), exposure surface (external mobile vs internal), deployment cadence and security level are all different. The principle inside the engine repository of "canonical document = same commit as code" is kept as is inside each repository | Adding agent screens to the engine's `commission-api` — an external exposure surface would live in the same process as the money ledger |
| D-2 | **Engine as per-tenant instances (physical separation), portal as multi-tenant (logical separation + RLS)** | Deploying the money ledger separately per GA meets the customers' (GAs') audit and isolation requirements and requires no engine code change. The portal is read-only, so logical separation is sufficient and operating cost is low | Making the engine multi-tenant — major surgery adding a tenant axis to every ledger, lock and close |
| D-3 | **CQRS read model + outbox pull feed** (no broker) | Agent query patterns (key lookups by agent × month, concentrated on the morning after close) do not fit the engine's calculation ledger schema. Queries must be possible regardless of engine outages. Cursor-based pull without a broker keeps running the portfolio simple, and switching to Kafka or the like only means replacing one ingest adapter | Synchronous engine query per screen — dependent on engine availability, shifts peak-time load onto it |
| D-4 | **After-tax provisional figures and simulations via the engine's dry-run API** | Withholding tax, offset order and clawback rates are engine rules. The moment the portal imitates them, the two numbers diverge (premise 1) | Multiplying by 3.3% in the portal — tax rates and offset conventions are parameters, so it will be wrong someday |
| D-5 | **Portal DB is PostgreSQL** (the engine stays on Oracle) | Row-level security (RLS) lets the DB act as a second defense for tenant isolation; JSONB stores line bases. The read model is independent of the engine DB, so the choice is free | Unifying on Oracle — if a customer's standard is Oracle, it can be handled by replacing adapters (domain and projectors are DB-independent) |
| D-6 | **Mobile-first web (PWA)**, no native app | Webview embedding in the adopting GA's existing app is the realistic distribution path. One codebase | Native — exceeds portfolio scope |

### 3.2 Component composition

```
[ga-commission-engine  (per-tenant instance, Oracle)]
   │  OUTBOX_EVENT (loaded in the same transaction from calculation, ledger, deferral, clawback, close and payout hooks)
   │
   ├─ GET  /internal/v1/events?afterSeq=…            ──┐ (pull, cursor)
   ├─ POST /internal/v1/simulations/new-contract     ──┤ (synchronous)
   ├─ POST /internal/v1/simulations/policy-event     ──┤
   └─ GET  /internal/v1/agents/{id}/statements/{ym}  ──┘ (reconciliation, provisional statement)
                                                       │
[ga-agent-portal (multi-tenant, PostgreSQL)]           │
   portal-ingest   ─ per-tenant poller → idempotent apply → projector → notification derivation
   portal-query    ─ /api/v1/me/**  (agent self queries, agent_id only from the token)
   portal-sim      ─ engine synchronous call proxy + simulation_log
   portal-recon    ─ daily reconciliation, DRIFT handling, rebuild command
   web/            ─ React PWA (home, limits, deferrals, simulation, statements, notifications)
   ▲
   │ OIDC (per-tenant IdP / Keycloak for development)
[agent smartphone/tablet / webview in the GA's existing app]
```

### 3.3 Module composition (Gradle multi-module + pnpm)

```
ga-agent-portal/
├── contracts/                # engine↔portal contract: JSON Schema (events/v1/*.json, api/v1/openapi.yaml) + shared/ga-disclosure/ (copy of the shared Envelope core, v1.2)
├── portal-domain             # portal-specific value objects (CalcId, PolicyNo, LedgerId, ScheduleId, CommType), view models, status enums. Depends only on platform-core
├── portal-projection         # projectors (event→view transformation) pure logic + notification derivation rules. DB-independent
├── portal-ingest             # event feed client, cursor, idempotent apply, failure isolation, rebuild executor
├── portal-query              # query services, REST controllers, authorization policy
├── portal-sim                # engine simulation and statement dry-run proxy, simulation_log
├── portal-recon              # reconciliation batch, DRIFT state transitions, observability metrics
├── portal-infra              # PostgreSQL adapters (repositories under platform-spring `TenantScopedRepository`), RLS session binding, Flyway
├── portal-app                # Spring Boot assembly, security configuration, schedulers
├── portal-demo               # demo tenant data generator (§Appendix B), event replayer standing in for a local engine
└── web/                      # React 19 + TypeScript + Vite (PWA)
```

Dependency direction: `app → query/ingest/sim/recon → projection → domain`. `infra` is assembled as an adapter only in `app`. `projection` must be independent of DB and Spring (millisecond-level tests with in-memory repositories), the same principle as the engine's `commission-calc`.

**Platform artifact (v1.2)**: the shared value objects `Won`·`Ratio`·`Ym`·`TenantId`·`AgentId` and `TenantContext` come from `com.ga.platform:platform-core` 0.2.0 published by ga-disclosure (§11 platform row). The portal does not create classes with the same simple names (architecture test), and if the platform API falls short, the portal does not supplement it but escalates it as maintenance work on the ga-disclosure side. The only external module `portal-domain` and `portal-projection` may depend on is `platform-core`; `platform-spring` (session binding, repository base classes) is used only in `infra` and `app`.

### 3.4 Three data flows

1. **Projection path (asynchronous)**: engine transaction commit → OUTBOX load → portal poller (per tenant, every 5–30 seconds) → `ingest_event` idempotency check → projector apply + notification derivation (same transaction) → cursor advance. Screens always display `asOf(seq, at)` alongside.
2. **Synchronous path**: simulation or provisional statement refresh request → `portal-sim` → engine API (service token) → result log → response. When the engine is unavailable, the screen shows "try again shortly" + the time of the last snapshot, and **projection-path data continues to be served as is**.
3. **Reconciliation path (batch)**: every day at dawn, per tenant, the current month's (ESTIMATE) and the previous close month's (CONFIRMED) statements are fetched from the engine and compared with the portal's sums → on mismatch, that agent × month is marked `DRIFT` and the screen shows a "Verifying" badge → automatic rebuild attempt → if it persists, operations alert.

---

## 4. Engine↔Portal Integration Contract (Integration Contract v1)

The contract lives in the `contracts/` folder as JSON Schema and OpenAPI. The engine repository has **provider tests that validate published events** against the same schemas (§13). Schema changes follow incrementing the `version` field and the compatibility rules (§4.5).

**Contract conventions (v1.2)**:
- `contracts/` has two parts. `contracts/shared/ga-disclosure/` is a copy of the **shared Envelope core** (`events/v1/envelope-core.schema.json`) owned by ga-disclosure and is not edited — if it needs to change, a small PR in ga-disclosure → merge → move `UPSTREAM` to that merge commit and fetch it again. Everything else (`events/v1`·`api/v1`) is portal canonical.
- The copy directory has its own `CHECKSUMS` (plain SHA-256 of file bytes, `sha256sum -c` format) and `UPSTREAM` (first line `hjryoo-ai/ga-disclosure@<40-character merge commit>`, file list from the next line on). `verifyContractChecksums` (offline, `check`) checks the portal-canonical `contracts/CHECKSUMS` and the copy's `CHECKSUMS`; `verifyUpstreamContract` (network, CI) byte-compares **only the copy** with upstream — comparing all of `contracts/` would fail because the portal-canonical files do not exist upstream. JCS (`platform-canonical`) is used only for the canonical hash of event payloads (idempotency and duplicate detection).
- Amounts are `integer` (integer won). Dates, timestamps and UUIDs use `pattern`, not `format` (the consuming validator does not assert `format`) — zero `format` keywords in all files under `contracts/` (tested). **Non-money decimals** such as rates, payout rates and multiples are **opaque decimal strings, not JSON numbers**: `^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$` (display only — consumers do not use them in arithmetic; a JSON number produces a `double` or `BigDecimal` the moment it is parsed): `basis.rate`·`basis.payoutRate`·`LimitLedgerOpened.limitMultiple`.
- **Contract v1.1** (v1.3): fields whose data the engine does not have are not mandatory — the engine is not made to invent values. Optional are `policySummary.productName`·`insuredMasked` (the engine has no policy master or customer data), `basis.rate`·`payoutRate`·`installmentNo` (absent for flat-amount and lump-sum payouts — only `ruleVersionIds` is mandatory), `AgentSnapshot.grade`·`role` (agents without grade history; the engine has no roles), `StatementConfirmed.clawbackOffset`·`withholdingTax`·`net` (confirmed in the payout run). Display when optional fields are absent is in §8; the source of roles is in §9.

### 4.1 Event feed

**Engine side** (v1.4 — canonical source is engine design document §6.8 "Outbox and event feed", engine E1)

- Within the business transaction the engine inserts rows into `OUTBOX_EVENT` without a `seq`, and a **sequencer** attaches gap-free `seq` values only to committed rows (not an Oracle sequence — sequences develop gaps through rollbacks and caching, and if a lower number commits late, the poller skips it). The feed returns only rows that have a `seq`. Therefore the `seq` the portal sees **has no gaps**; a gap is a defect signal, so the portal stops that tenant (§6.1).
- The order between two facts on the same ledger or the same agent is guaranteed in commit order by the engine's locks (ledger `FOR UPDATE`, per-payee payout transaction). The order of two facts that do not share a lock is not guaranteed, and the portal does not rely on it.
- Append-only (engine trigger). Retention reduction (`TenantSnapshot`) is undecided (§14.1 #4).
- Only agent (AGENT) payee rows are published — organization (ORG) payees (overrides) do not appear in v1 (engine E1 D-3).

**Feed API**

```
GET /internal/v1/events?afterSeq={n}&limit={1~1000, default 200}
→ { "tenantId": "GA01", "events": [Envelope...], "nextSeq": n', "headSeq": h, "schemaVersion": 1 }
```

- Response order is ascending `seq`, no gaps. `headSeq − nextSeq` is the portal's lag metric.
- `tenantId` (mandatory, v1.3) is the tenant of the responding engine instance. Since the engine is a per-tenant instance, the portal uses the response to prevent the accident of reading another tenant through a wrong `engine_base_url` — if it differs from the requested tenant, nothing is applied and that tenant is stopped (§6.1).
- `afterSeq > headSeq` → `422` Problem `{code: AFTER_BEYOND_HEAD, message}` (a different publisher such as a restored DB — the same convention as the ga-disclosure feed). Missing or wrong service token → `401`. Parameter errors (afterSeq missing or negative, limit out of range) → `400` Problem `{code: INVALID_REQUEST}` (v1.4.1 — engine E1 already returns it).
- Authentication: service token (mTLS recommended in operations). Never accessible with an agent token.

**Envelope**

```json
{
  "eventId": "6f1c…", "seq": 10231, "type": "CommissionCalculated", "version": 1,
  "occurredAt": "2026-09-22T10:15:30+09:00",
  "aggregate": { "kind": "COMM_CALC", "id": "884213" },
  "payload": { ... }
}
```

The portal Envelope schema (`contracts/events/v1/envelope.schema.json`, v1.2) is `allOf: [shared core, portal constraints]`. The core is seven fields (`eventId` UUID, `seq` ≥ 1, `type`, `version` ≥ 1, `occurredAt` including offset, `aggregate{kind, id}`, `payload` object) and their formats; the portal constraints are 13 `type` values (§4.2), `version` 1, 9 `aggregate.kind` values and an **`if/then` per `type` (+`version`)** (payload schema, `type` ↔ `kind` pairing), and any `type` or `version` outside the enumeration is rejected (else: false). Payloads are not selected with `oneOf` — that is incompatible with open payloads that allow unknown fields (a payload whose fields overlap would match both and fail, and a payload unrelated to its `type` would also pass).

| `aggregate.kind` | Events | `aggregate.id` |
|---|---|---|
| `AGENT` | `AgentSnapshot` | agentId |
| `COMM_CALC` | `CommissionCalculated` | calcId |
| `ADJUSTMENT` | `AdjustmentPosted` | adjId |
| `LIMIT_LEDGER` | `LimitLedgerOpened`·`LimitLedgerPosted`·`LimitLedgerRebased` | ledgerId |
| `DEFERRAL_SCHEDULE` | `DeferralScheduled`·`DeferralStatusChanged` | scheduleId |
| `CLAWBACK_RECEIVABLE` | `ClawbackReceivableUpdated` | receivableId |
| `POLICY` | `PolicyEventObserved` | policyNo |
| `STATEMENT` | `StatementEstimated`·`StatementConfirmed` | `{agentId}:{closeYm}` (enforced by regex) |
| `PAYOUT` | `PayoutCompleted` | `{agentId}:{closeYm}` (enforced by regex) |

### 4.2 Event catalog

| Type | When it occurs (engine) | Key payload | Portal projector effect |
|---|---|---|---|
| `AgentSnapshot` | On change of agent master data, affiliation or grade; on rebuild bootstrap | agentId, nameMasked?, name, grade?, orgPath, role?, status, validFrom (v1.3: grade and role optional — roles come from §9 `identity_link`) | `agent_profile` upsert (state fact — v1.5. `status` ∉ {ACTIVE, DISMISSED} → poison `STATE_MISMATCH`; NULL if `role` is absent) |
| `CommissionCalculated` | Post-save hook of `COMM_CALC` | calcId, agentId, policyNo, productKey, commType, amount (integer won, negative for clawbacks and cancellations. **The scheduled payout amount after the limit cut and after the deferral split** — requested amount = amount + limitCutAmt; the deferred portion arrives separately as `DeferralScheduled` (CURVE·LIMIT_DEFER) and comes back as a DEFERRED line when due, v1.3), closeYm, reversalOfCalcId?, limitCutAmt, limitCutPolicy (CUT/DEFER_AFTER_FY — the rule value as is. The current engine treats DEFER_AFTER_FY the same as a cut, v1.3.1)?, basis{rate?, payoutRate?, installmentNo?, ruleVersionIds[]}, policySummary{contractDate, monthlyPremium, productName?, insuredMasked?} | **Immutable fact** (v1.5): `earnings_line` insert — if the same `calcId` arrives again, poison `DUPLICATE_KEY`. Upsert of that month's `earnings_month` (sum = one aggregation over that month's lines — poison `CLOSED_MONTH` if it is a closed month); if there is a cut, the `cut_total`/`deferred_total` of that policy's ledger (no ledger → `UNKNOWN_REFERENCE`) |
| `AdjustmentPosted` | Registration of a post-close correction | adjId, targetCalcId, agentId, amount, closeYm (following month), reason | **Immutable fact** (v1.5): `earnings_line` (commType=ADJUSTMENT, key `ADJ:`+adjId — so as not to collide with calculation keys; product and policy copied from the target line; no target → `UNKNOWN_REFERENCE`; arriving again → `DUPLICATE_KEY`), that month's (following month's) `adjustment` |
| `LimitLedgerOpened` | Limit ledger opened | ledgerId, policyNo, agentId, contractDate, fyStart, fyEnd, monthlyPremium, limitMultiple, limitAmount, ruleVersionId | **Immutable fact** (v1.5): `policy_limit` insert — the same ledger or the same `(policyNo, agentId)` is poison `DUPLICATE_KEY` |
| `LimitLedgerPosted` | `LimitLedgerPoster` posting | ledgerId, postingSeq, calcId, amount, accumAfter | `policy_limit.accum_paid = accumAfter`, history append (key is event `seq` — DD-5), threshold notification. Does not require a line. Engine rules (v1.5 — DD-8): sequence = previous + 1, `accumAfter − accum_paid = amount`, `≤ limit` after a positive posting, accumulated ≥ 0 — otherwise poison |
| `LimitLedgerRebased` | Limit recalculation due to premium change | ledgerId, postingSeq? (v1.3.1 — the last posting sequence before the recalculation; absent if the ledger had no postings), oldLimit, newLimit, monthlyPremium, reason | Limit amount update (`oldLimit` = stored value; sequence, if present, = last posting sequence), history append (posting sequence may be absent — key is event `seq`), notification |
| `DeferralScheduled` | Hook after `DeferralSplitStep` confirmation | scheduleId, sourceCalcId, agentId, policyNo, dueYm, amount, payCondition, curveVersionId, reason (CURVE/LIMIT_DEFER) | **Immutable fact** (v1.5): `deferral_item` insert (`SCHEDULED`) — arriving again → `DUPLICATE_KEY` |
| `DeferralStatusChanged` | Due-date batch, surrender event | scheduleId, from, to (RELEASED/HELD/CANCELLED), reason (v1.4.1 closed enumeration — engine vocabulary: DUE_RELEASED/POLICY_NOT_INFORCE/POLICY_TERMINATED/SOURCE_REVERSED) | Status update (stored status = `from`, only engine transition–reason pairs — §6.2, DD-9; unknown item `UNKNOWN_REFERENCE`), notification (HELD/CANCELLED) |
| `ClawbackReceivableUpdated` | Receivable creation, offset | receivableId, agentId, originCalcId, amount (≥ 0), remaining (≥ 0), status (v1.4.1 closed enumeration — engine vocabulary: OPEN/OFFSET (partially offset)/CLOSED/WRITTEN_OFF) | `clawback_item` upsert (state fact — v1.5. Same agent, origin line and amount; remaining never increases; no update after termination; `CLOSED ⇒ remaining 0`) |
| `PolicyEventObserved` | After INBOUND normalization | policyNo, agentId, eventType (CANCEL/LAPSE/WITHDRAW/REDUCE/REINSTATE/PAYMENT_OVERDUE — REINSTATE = engine REVIVE; PAYMENT_OVERDUE is not published by our engine and is for other sources), occurredOn | `policy_flag` update (type → upsert of 5 flag kinds, `reason` = original type, REINSTATE clears all open flags — state fact), notification |
| `StatementEstimated` | Nightly batch (payout logic dry-run) | agentId, closeYm, gross, clawbackOffset, adjustment, withholdingTax, net, basisSeq, asOf | Updates `earnings_month` tax and net amounts (estimated, `net_basis ESTIMATE`), `estimate_as_of`, `estimate_basis_seq`; status=ESTIMATED (poison if it is a closed month). Totals are not reconciled |
| `StatementConfirmed` | Month-end close confirmation | agentId, closeYm, gross, clawbackOffset?, adjustment, withholdingTax?, net?, closedAt (v1.3: withholding tax, offset and net are confirmed in the payout run, so optional) | status=CONFIRMED (afterwards lines of that month must not change — a new line is poison `CLOSED_MONTH`, corrections go through `AdjustmentPosted`). Creates the row if the month has no lines. A total mismatch is not poison but `DRIFT` (§6.6 — v1.5) |
| `PayoutCompleted` | Payout run completed | agentId, closeYm, paidAt (payout confirmation time — not transfer execution, v1.3.1), paidAmount (= net), runSeq?, gross?, clawbackOffset?, receivableOffset?, carriedReceivable?, adjustment?, payable?, withholdingTax?, net? (v1.3: confirmed values of the payout run) | status=PAID (only from CONFIRMED), confirmed tax amount, actual payout amount, receivable offset, carry-over, payable amount, run sequence (`net_basis PAYOUT`); reconciles only the totals present (§6.6), notification |

**Definition of aggregate amounts** (v1.3 — the same sentence in the contract description and the §5 comments): `gross` = Σ amount of that month's `CommissionCalculated` lines with commType ∉ {CLAWBACK, ADJUSTMENT} — **summed across all statuses** (both cancelled originals and reversals; not filtered by status), `clawbackOffset` = Σ that month's CLAWBACK lines (≤ 0), `adjustment` = Σ `AdjustmentPosted` attributed to that month. The three fields of `StatementEstimated`, `StatementConfirmed` and `PayoutCompleted` follow this definition, and they must equal the sums in the portal's `earnings_month` for the §6.6 reconciliation to hold. `PayoutCompleted.receivableOffset` (automatic offset of existing receivables) is different from `clawbackOffset`.

Clawback is not a separate type. In the engine a clawback is a negative `COMM_CALC`, so it arrives as `CommissionCalculated(commType=CLAWBACK, amount<0)`, and only whether it became a receivable is supplemented by `ClawbackReceivableUpdated`.

### 4.3 Synchronous API (provided by the engine)

| API | Request | Response | Purpose |
|---|---|---|---|
| `POST /internal/v1/simulations/new-contract` | agentId, productKey, monthlyPremium, contractDate, paymentTermMonths?, insurerCd? (v1.4.2 — the rate key is (insurer, product). When absent, if the number of candidate insurers is not exactly 1 (0 or 2+), 422 `INSURER_AMBIGUOUS` — v1.5.1), incentiveCodes[]? (engine incentive codes — if given, only those incentives) | limitAmount?·projectedAccum? (v1.4.2 — absent means a policy with no limit applied), fyCommissionByInstallment[{'no', amount}] (one per installment — key in quotes, v1.5.1), incentives[{code, amount (after the cut — same definition as `CommissionCalculated.amount`), limitCutAmt?, limitIncluded}] (summed per code), deferral{firstYearPayout (= Σ installment commissions + Σ incentives), schedule[{dueYm, amount}] (summed per dueYm)}, ruleSnapshotId, generatedAt · 400 · 404 `AGENT_NOT_FOUND` · 422 | Q5 |
| `POST /internal/v1/simulations/policy-event` | agentId, policyNo, eventType, asOfDate, elapsedInstallments? (elapsed installment count — if absent, approximated by number of months), newMonthlyPremium? (recalculation input for REDUCE) | clawbackAmount (sign is the same as `CommissionCalculated` — surrender and lapse ≤ 0, reinstatement repayment > 0, v1.5.1), offsetAgainstMonth{closeYm, before, after} (before and after of that month's pre-tax net before receivable offset = gross + clawbackOffset), receivableRemaining (sum of open receivable balances after this event = open receivable balance + max(0, −after), v1.5.1), cancelledDeferrals[{scheduleId, dueYm, amount}], ruleSnapshotId · 400 · 404 `AGENT_NOT_FOUND`·`POLICY_NOT_FOUND` · 422 `UNSUPPORTED_EVENT_TYPE` (PAYMENT_OVERDUE) | Q4 |
| `GET /internal/v1/agents/{agentId}/statements/{ym}?mode=ESTIMATE\|CONFIRMED` | — | ESTIMATE = the provisional statement computed now (in the shape of StatementEstimated, not published), CONFIRMED = the shape of StatementConfirmed + withholdingTax·net after payout (sum of that month's payout runs) — the same values as that month's events (v1.4.2) · 400 · 404 `AGENT_NOT_FOUND`·`STATEMENT_NOT_CONFIRMED` · 422 `MONTH_CLOSED` (ESTIMATE for a closed month) | Reconciliation, on-demand refresh (rate-limited) |
| `GET /internal/v1/events` | afterSeq, limit? (1–1000, default 200) | tenantId, events, nextSeq, headSeq, schemaVersion · 400 `INVALID_REQUEST` · 401 · 422 `AFTER_BEYOND_HEAD` (§4.1) | Projection |

Simulation runs the engine pipeline **without persistence (dry-run)**. It does not take the limit ledger lock and only reads the ledger state (it calls the gate decision of engine design document §6.1 in read-only mode).

### 4.4 Engine repository change list (ga-commission-engine, separate PR)

1. `OUTBOX_EVENT` DDL (Flyway) + `OutboxPublisher implements CalcResultListener` — registered in the post-save hook chain.
2. The same hook applied to deferral status transitions (`commission-deferral`), receivable updates (`commission-clawback`), close and payout (`commission-settlement`), and limit ledger opening and recalculation (`commission-limit`).
3. `StatementEstimated` batch (`commission-batch`): runs the payout run logic in dry-run mode to publish a provisional statement per agent. The cycle is a batch parameter (**default once nightly**, confirmed §14.0). Delayed recalculation after events and on-demand refresh call the same dry-run service with different triggers, so changing the cycle must require no code change.
4. `commission-api`: event feed (E1), the 2 simulation controllers and the statement query controller (E2, absorbing the remainder of the existing roadmap Phase 9).
5. Provider validation tests against the `contracts/events/v1` schemas (run golden scenarios → the published event sequence matches the snapshot).
6. Invariant test that no `CommissionCalculated` for a `closeYm` is published after its close (close immutability tests already exist, so only the event aspect is added).

### 4.5 Contract versioning and compatibility rules

- **Adding** fields is compatible (the portal ignores unknown fields). **Removing a field, changing its meaning or changing its type** requires incrementing `version` + a version branch in the portal projectors.
- When the portal encounters an event with a version greater than the maximum it supports, it **stops ingest for that tenant** and raises an operations alert (stopping is better than showing misinterpreted numbers). Since the portal Envelope schema accepts only `version` 1, such an event is caught first by schema validation (v1.2).
- The **core field set** of the Envelope is a contract shared with ga-disclosure — the portal does not change it (v1.2, contract conventions above).
- `seq` gaps, out-of-order and duplicates mean stop, ignore and ignore respectively (§6.1).

---

## 5. Read model (portal schema, PostgreSQL)

Every table has `tenant_id` as its first column and includes it in its PK, UNIQUE constraints and indexes. Amounts are `BIGINT` (integer won). Only the core tables are described below; the DDL is managed by Flyway (canonical: `portal-infra/src/main/resources/db/migration/V1__init.sql`).

**Constraints (v1.2)** — no columns were added; only constraints were added to V1. ① Format `CHECK` (regex): `tenant_id` = platform `TenantId` (`^[A-Z0-9][A-Z0-9_]{0,31}$`), `agent_id` = platform `AgentId` (`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`), `close_ym` and `due_ym` = `Ym` (`yyyyMM`), `event_id` and `last_event_id` = the UUID regex of the Envelope core. ② Closed-value `CHECK (… IN …)`: the columns annotated with a `-- A/B/C` comment in the DDL below — 12 in v1.2 + 7 in V4 (v1.5) = **19**. The value list is that comment (for the 9 without an enum) or a portal enum (`MonthStatus`, `DataStatus`, `DeferralStatus`, `IngestState`, `IngestEventStatus`, `AgentStatus`, `ReceivableStatus`, `NetBasis`, `ReconSource`, `FlagType`), and `StatusEnumDdlTest` cross-checks the three (comment, V1–V4, enum). Open lists ending in `/...` (`comm_type`, `role`) have no `CHECK`. ③ The unique key of `notification` is scoped to the agent (Q1).

```sql
CREATE TABLE tenant (
  tenant_id        TEXT PRIMARY KEY,
  name             TEXT NOT NULL,
  engine_base_url  TEXT NOT NULL,                -- D-2: per-tenant engine instance
  -- meaning of status (v1.4.1, DD-6): administrative status (GA contract suspended, before onboarding). PAUSED = both polling and queries stop — independent of the cursor state (ingest_cursor.state = operational state)
  status           TEXT NOT NULL,                -- ACTIVE/PAUSED
  limit_warn_pct   NUMERIC(5,2) NOT NULL DEFAULT 80,   -- example value. The portal reads it in SQL as integer bp (round(x * 100)::bigint) and uses it only as a Ratio — no BigDecimal (v1.2)
  limit_crit_pct   NUMERIC(5,2) NOT NULL DEFAULT 95,   -- example value. Same convention
  payout_day_rule  TEXT NOT NULL,                -- e.g. "NEXT_MONTH_D10" (for displaying the scheduled payout date)
  params           JSONB NOT NULL DEFAULT '{"org_member_lines_visible": false, "forecast_scenario_enabled": false, "notification_digest": false}'
                                                 -- company-policy parameters. Confirmed keys (§14.0) — the default holds three keys (v1.2):
                                                 --   org_member_lines_visible : bool, default false (shows individual team members' lines in the org manager view)
                                                 --   forecast_scenario_enabled: bool, default false (retention-rate assumption toggle on the deferral timeline)
                                                 --   notification_digest      : bool, default false
                                                 --   sync_delay_seconds       : int; if absent, the setting portal.query.default-sync-delay-seconds (default 300) — v1.6, §7 dataStatus
  oidc_issuer      TEXT UNIQUE,                  -- v1.6 V5: iss of this tenant's tokens (exact match). If both this and oidc_audience are NULL, the tenant cannot log in (§9)
  oidc_audience    TEXT                          -- v1.6 V5: value that must be present in the token's aud. Only the dedicated role portal_issuer reads these two columns, before tenant binding
);

CREATE TABLE identity_link (            -- IdP subject ↔ agent mapping. agent_id and role are determined only here (v1.6 — not by token claims)
  tenant_id TEXT, subject TEXT,
  agent_id  TEXT,                        -- v1.6 V5: NULL only for OPERATOR (CHECK — the operations CLI principal cli:<OS user> has no agent)
  role      TEXT NOT NULL,               -- AGENT/MANAGER/OPERATOR
  status    TEXT NOT NULL,               -- ACTIVE/REVOKED
  org_path  TEXT,                        -- v1.6 organization for authorization (portal fact, v2 org manager scope) — required except for OPERATOR. A different source from agent_profile.org_path (engine fact, for display)
  linked_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (tenant_id, subject)
);

CREATE TABLE agent_profile (
  tenant_id TEXT, agent_id TEXT, name TEXT NOT NULL, grade TEXT, org_path TEXT NOT NULL, -- org_id path exactly as given by the engine (e.g. /HQ1/BR03/TM07)
  role TEXT,                             -- AGENT/TEAM_LEAD/BRANCH_HEAD/... (v1.5 NULL allowed — the engine has no role; roles come from identity_link, Phase 3)
  status TEXT NOT NULL,                  -- ACTIVE/DISMISSED
  valid_from DATE NOT NULL, last_seq BIGINT NOT NULL,
  PRIMARY KEY (tenant_id, agent_id)
);

CREATE TABLE earnings_line (
  tenant_id TEXT, calc_id TEXT, agent_id TEXT NOT NULL, policy_no TEXT NOT NULL,
  product_key TEXT, comm_type TEXT NOT NULL,     -- FY_COMM/RENEWAL/INCENTIVE/OVERRIDE/DEFERRED/CLAWBACK/ADJUSTMENT/...
  amount BIGINT NOT NULL, close_ym TEXT NOT NULL,  -- amount = payout after the limit cut (requested amount = amount + limit_cut_amt, v1.2 I1)
  reversal_of_calc_id TEXT, limit_cut_amt BIGINT NOT NULL DEFAULT 0, limit_cut_policy TEXT,
  basis JSONB NOT NULL, policy_summary JSONB NOT NULL, source_seq BIGINT NOT NULL,  -- rate and payoutRate in basis are opaque decimal strings (display only, v1.2)
  PRIMARY KEY (tenant_id, calc_id)
);
CREATE INDEX ix_line_agent_ym ON earnings_line (tenant_id, agent_id, close_ym);
CREATE INDEX ix_line_policy   ON earnings_line (tenant_id, agent_id, policy_no);

CREATE TABLE earnings_month (
  tenant_id TEXT, agent_id TEXT, close_ym TEXT,
  gross BIGINT NOT NULL DEFAULT 0,         -- portal sum: Σ of the month's CommissionCalculated lines with comm_type ∉ {CLAWBACK, ADJUSTMENT} — summed across all states (both reversed originals and reversals)
  clawback_offset BIGINT NOT NULL DEFAULT 0, -- portal sum: Σ of the month's CLAWBACK lines (≤ 0)
  adjustment BIGINT NOT NULL DEFAULT 0,    -- portal sum: Σ of AdjustmentPosted attributed to the month (comm_type ADJUSTMENT lines)
  withholding_tax BIGINT,                  -- filled only from engine statement snapshots (NULL = not yet available)
  net BIGINT,                              -- filled only from engine statement snapshots
  status TEXT NOT NULL,                    -- PROVISIONAL/ESTIMATED/CONFIRMED/PAID
  -- data_status: DRIFT = the totals of a close/payout event differ from the portal sum (§6.6, not poison). REBUILDING is unused (v1.5 Q9 — the rebuild signal is the cursor state)
  data_status TEXT NOT NULL DEFAULT 'OK',  -- OK/DRIFT/REBUILDING
  estimate_as_of TIMESTAMPTZ, estimate_basis_seq BIGINT,  -- asOf and basisSeq of StatementEstimated (recorded without checks)
  confirmed_at TIMESTAMPTZ, paid_at TIMESTAMPTZ, paid_amount BIGINT,
  receivable_offset BIGINT, carried_receivable BIGINT, payable BIGINT, run_seq BIGINT,  -- v1.5: confirmed payout-run values from PayoutCompleted (engine values)
  net_basis TEXT,                          -- ESTIMATE/CONFIRM/PAYOUT
  last_seq BIGINT NOT NULL,
  PRIMARY KEY (tenant_id, agent_id, close_ym)
);

CREATE TABLE policy_limit (
  tenant_id TEXT, ledger_id TEXT, agent_id TEXT NOT NULL, policy_no TEXT NOT NULL,
  contract_date DATE NOT NULL, fy_start DATE NOT NULL, fy_end DATE NOT NULL,
  monthly_premium BIGINT NOT NULL, limit_multiple TEXT NOT NULL, limit_amount BIGINT NOT NULL,  -- limit_multiple is an opaque decimal string for display only (never multiplied) — since v1.6 V5 the contract's decimalString verbatim (TEXT)
  accum_paid BIGINT NOT NULL DEFAULT 0,    -- stores the engine's accumAfter as is (no portal summation)
  cut_total BIGINT NOT NULL DEFAULT 0, deferred_total BIGINT NOT NULL DEFAULT 0,
  last_posting_seq BIGINT NOT NULL DEFAULT 0, rule_version_id TEXT NOT NULL,
  PRIMARY KEY (tenant_id, ledger_id),
  UNIQUE (tenant_id, policy_no, agent_id)
);
CREATE TABLE policy_limit_history (       -- ledger timeline (postings and rebases)
  tenant_id TEXT, ledger_id TEXT, source_seq BIGINT NOT NULL,  -- v1.5: key = the event seq of that fact (DD-5 — a REBASE may have no posting sequence number)
  posting_seq BIGINT, kind TEXT NOT NULL,  -- POST/REBASE
  calc_id TEXT, amount BIGINT, accum_after BIGINT, limit_after BIGINT, reason TEXT, occurred_at TIMESTAMPTZ,
  PRIMARY KEY (tenant_id, ledger_id, source_seq)
);

CREATE TABLE deferral_item (
  tenant_id TEXT, schedule_id TEXT, agent_id TEXT NOT NULL, policy_no TEXT NOT NULL,
  source_calc_id TEXT NOT NULL, due_ym TEXT NOT NULL, amount BIGINT NOT NULL,
  pay_condition TEXT NOT NULL, status TEXT NOT NULL,   -- SCHEDULED/RELEASED/HELD/CANCELLED
  reason TEXT NOT NULL,                  -- CURVE/LIMIT_DEFER
  curve_version_id TEXT NOT NULL,
  status_reason TEXT,                    -- DUE_RELEASED/POLICY_NOT_INFORCE/POLICY_TERMINATED/SOURCE_REVERSED
  last_seq BIGINT NOT NULL,
  PRIMARY KEY (tenant_id, schedule_id)
);
CREATE INDEX ix_defer_agent_due ON deferral_item (tenant_id, agent_id, due_ym);

CREATE TABLE clawback_item (
  tenant_id TEXT, receivable_id TEXT, agent_id TEXT NOT NULL, origin_calc_id TEXT NOT NULL,
  amount BIGINT NOT NULL, remaining BIGINT NOT NULL, last_seq BIGINT NOT NULL,
  status TEXT NOT NULL,                  -- OPEN/OFFSET/CLOSED/WRITTEN_OFF
  PRIMARY KEY (tenant_id, receivable_id)
);

CREATE TABLE policy_flag (
  tenant_id TEXT, policy_no TEXT, agent_id TEXT, flag_type TEXT,   -- CANCELLED/LAPSED/OVERDUE/REDUCED
  occurred_on DATE NOT NULL, cleared_at TIMESTAMPTZ,
  reason TEXT,                           -- CANCEL/LAPSE/WITHDRAW/REDUCE/PAYMENT_OVERDUE
  PRIMARY KEY (tenant_id, policy_no, agent_id, flag_type)
);

CREATE TABLE notification (
  tenant_id TEXT, notification_id UUID, agent_id TEXT NOT NULL, type TEXT NOT NULL, severity TEXT NOT NULL,  -- INFO/WARN/CRIT
  dedup_key TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, link TEXT,
  created_at TIMESTAMPTZ NOT NULL, read_at TIMESTAMPTZ,
  PRIMARY KEY (tenant_id, notification_id), UNIQUE (tenant_id, agent_id, dedup_key)  -- v1.2 Q1: dedup_key is unique per agent
);

CREATE TABLE inquiry (
  tenant_id TEXT, inquiry_id UUID, agent_id TEXT NOT NULL, calc_id TEXT, category TEXT NOT NULL,
  message TEXT NOT NULL, status TEXT NOT NULL,   -- OPEN/ANSWERED/CLOSED
  created_at TIMESTAMPTZ NOT NULL, answered_at TIMESTAMPTZ, answer TEXT, answered_by TEXT,
  PRIMARY KEY (tenant_id, inquiry_id)
);

CREATE TABLE simulation_log (
  tenant_id TEXT, sim_id UUID, agent_id TEXT NOT NULL, kind TEXT NOT NULL,
  request JSONB NOT NULL, response JSONB, rule_snapshot_id TEXT, status TEXT NOT NULL,  -- REQUESTED/SUCCEEDED/FAILED
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (tenant_id, sim_id)
);

CREATE TABLE ingest_cursor (
  tenant_id TEXT PRIMARY KEY, last_seq BIGINT NOT NULL, last_event_id TEXT, state TEXT NOT NULL, -- RUNNING/PAUSED/FAILED/REBUILDING
  updated_at TIMESTAMPTZ NOT NULL, error TEXT,   -- updated_at = time of the last apply, stop or operator command (idle polling does not change it)
  caught_up_at TIMESTAMPTZ                       -- v1.6 V5: last time a round received the feed and ended with last_seq = headSeq and RUNNING — the basis for §7 SYNC_DELAYED
);
CREATE TABLE ingest_event (                -- idempotency key + apply audit
  tenant_id TEXT, event_id TEXT, seq BIGINT NOT NULL, event_type TEXT NOT NULL,
  received_at TIMESTAMPTZ NOT NULL, status TEXT NOT NULL,   -- APPLIED/SKIPPED/FAILED
  error TEXT,
  payload_hash CHAR(64) NOT NULL,            -- V3: JCS SHA-256 hex of {type, version, occurredAt, aggregate, payload}
  PRIMARY KEY (tenant_id, event_id)
);

CREATE TABLE recon_result (
  tenant_id TEXT, agent_id TEXT, close_ym TEXT, run_at TIMESTAMPTZ,
  portal_gross BIGINT, portal_offset BIGINT, portal_adjustment BIGINT,
  engine_gross BIGINT, engine_offset BIGINT, engine_adjustment BIGINT, engine_net BIGINT,
  status TEXT NOT NULL,                    -- MATCH/DRIFT/REPAIRED
  source_seq BIGINT NOT NULL,              -- v1.5: EVENT = that event's seq, BATCH = 0 (confirm and payout DRIFTs at the same time are told apart by the key — Q2)
  source TEXT NOT NULL,                    -- EVENT/BATCH
  PRIMARY KEY (tenant_id, agent_id, close_ym, run_at, source, source_seq)
);

CREATE TABLE audit_access (                -- audit of viewing others' data and of administrative actions
  tenant_id TEXT, id BIGSERIAL, actor_subject TEXT, actor_agent_id TEXT, action TEXT, target TEXT,
  at TIMESTAMPTZ NOT NULL, PRIMARY KEY (tenant_id, id),
  detail TEXT                                -- v1.6 V5: denial reason code (AUTHZ_DENIED — closed values; holds no data values)
);
```

**Status columns**: the canonical value sets are the comments above, and the V1 `CHECK IN` must match them (`StatusEnumDdlTest`). If `simulation_log.status` (REQUESTED/SUCCEEDED/FAILED) needs a failure reason, do not add values; add an `error` column in a new V (in the same commit as the §5 revision — Phase 0 acceptance review F4).

**Ingestion table** (v1.4, V3): `ingest_event` keeps only applied events (`APPLIED`) and poison events whose key can be trusted (`FAILED`) — skipped events have no row (a row with the same `eventId` already exists, so a second row is impossible under the PK; the enum's `SKIPPED` is **unused** and appears only in the metric `ingest_events_total{result=skipped}` — Phase 1 approval Q2). `payload_hash` is the JCS SHA-256 of `{type, version, occurredAt, aggregate, payload}` (excluding `seq`, `eventId` and unknown top-level fields — Q1); the payload body is not stored.

**RLS** (v1.2): every tenant table (including `tenant`) has `ENABLE` + `FORCE ROW LEVEL SECURITY` and the policy `tenant_isolation USING/WITH CHECK (tenant_id = current_setting('app.tenant_id', true))` — canonical `V2__rls.sql`. When unset the setting is `NULL`, so queries return 0 rows without error, and `tenant` too shows only its own row. platform-spring's `TenantSessionBinder` sets the tenant at transaction start with `SELECT set_config('app.tenant_id', ?, true)` (transaction-local — same effect as `SET LOCAL`). The setting name is the public constant `TenantSession.SETTING_NAME`, and `RlsSettingNameTest` checks it against the migration strings (because a misspelling would make the policy always read `NULL` and silently return 0 rows). The app role is an ordinary role that cannot bypass RLS, and V2 asserts that every portal role is `NOBYPASSRLS` and `NOSUPERUSER` (§9 role table). The migrator also obeys the policies (`FORCE`), so **data migrations (backfills, rebuild swaps) enumerate the tenants and run per tenant after setting `set_config('app.tenant_id', …, true)`** — without the setting they see 0 rows and writes are rejected by `WITH CHECK` (Phase 0 acceptance review B1; to be revisited in the §6.7 rebuild design).

**Invariants (continuously verified by tests)**
- `earnings_month.gross/clawback_offset/adjustment = Σ earnings_line` (per tenant, agent and month).
- Ledger (v1.5 — the engine's `LimitLedger.post` rule, **DD-8**): **after a positive posting** `accum_after ≤ limit_amount`; after every posting `accum_after ≥ 0`, `accum_after − previous accum_after = amount`, and `postingSeq` = previous + 1. A rebase (`LimitLedgerRebased`) does not change the accumulated amount and has no checks — after a downward rebase, `accum_paid > limit_amount` is normal (Appendix A-3). On violation, applying the event fails → ingestion stops; this signals an engine anomaly.
- Lines of a month whose `status` is CONFIRMED/PAID are not changed by later events (an attempted change → ingestion stops + operations alert).

---

## 6. Core logic

### 6.1 Ingestion conventions

(v1.4 — Phase 1 directive D-1–D-5, plan ③, ④, ⑤, approval Q1–Q7. Canonical code: `portal-domain` `IngestTransitions`, `IngestErrorCode`; `portal-ingest` `IngestApplier`, `IngestWorker`, `EnvelopeValidator`, `PayloadHasher`)

- **A single worker per tenant** (ordering guarantee). A round: read the cursor in tx₀ (create it as `RUNNING`/0 if absent) and end if it is not `RUNNING` → call the feed (outside any transaction, `afterSeq = last_seq`) → for each event, **one DB transaction**: `pg_try_advisory_xact_lock` (if it cannot be acquired, another instance is processing — end of round) → re-read the cursor (decisions use only the values within this transaction) → receive validation → hash → comparison with the existing row → seq rule → `ingest_event` → projector → `ingest_cursor`. If one event stops, the round ends at that event and the events before it stay committed. No backoff after an apply failure — stopping is the convention.
- **Idempotency key and hash**: `(tenant_id, event_id)`; `payload_hash` = JCS SHA-256 (`{type, version, occurredAt, aggregate, payload}` — excluding `seq`, `eventId` and unknown top-level fields). Same eventId, same hash, same seq → **skip** (row and cursor unchanged, metric only).
- **seq rule**: lower than the expected `last_seq+1` with a known eventId → skip; lower with a **never-seen** eventId → poison (a new fact with a past number is a publisher anomaly); equal → apply; greater → gap → stop (`FAILED`). The engine numbers only committed rows, without gaps (engine design document §6.8) — a gap is a defect signal.
- **Poison events**: order of checks and what is recorded. The main transaction is rolled back, and the `FAILED` row (when the key can be trusted) and the cursor stop are written in a **separate transaction**. Other tenants are unaffected (there is no worker-wide state).

  | # | Check | code | Cursor | `ingest_event` |
  |---|---|---|---|---|
  | 0 | Response `tenantId` ≠ tenant | `TENANT_MISMATCH` | FAILED | None — nothing is applied |
  | 1 | Envelope core violation | `SCHEMA_VIOLATION` | FAILED | None — the key cannot be trusted |
  | 2 | `version` > supported maximum (`version.const` of the portal Envelope schema) | `UNSUPPORTED_VERSION` | **PAUSED** | None |
  | 3 | `type` ↔ `aggregate.kind` pairing (read from the `if/then` of the portal Envelope schema) | `TYPE_KIND_MISMATCH` | FAILED | FAILED row |
  | 4 | Portal Envelope/payload schema violation | `SCHEMA_VIOLATION` | FAILED | FAILED row |
  | 5 | Known eventId, different hash | `HASH_MISMATCH` | FAILED | None — protects the existing APPLIED row |
  | 6 | Known eventId, different seq | `EVENT_SEQ_CONFLICT` | FAILED | None |
  | 7 | Never-seen eventId, seq < expected | `PAST_SEQ_NEW_EVENT` | FAILED | FAILED row |
  | — | Never-seen eventId, seq > expected | `SEQ_GAP` | FAILED | None — the defect lies on the missing side |
  | 8 | Projector exception | `PROJECTOR_ERROR` | FAILED | FAILED row |
  | 9 | Any other apply-transaction error (DB constraint violations etc. — anything unclassified) | `APPLY_ERROR` | FAILED | None |

  If an eventId that has a `FAILED` row arrives again after a resume, it is judged again as never seen, and if it is applied, that row becomes `APPLIED`. `error` is `{code}: {detail}`, and the detail holds only the seq, eventId, expected value, schema location, keyword and exception name — no payload values, tokens or DB messages.
- **Feed failures**: 401 → `PAUSED(FEED_UNAUTHORIZED)`; if the token file is missing or empty, `PAUSED(TOKEN_MISSING)` before polling; 422 → `FAILED(AFTER_BEYOND_HEAD)`; network errors, 5xx, timeouts and format errors → state unchanged (retried in the next cycle, `ingest_feed_errors_total`).
- **State machine** (combinations not in the table are rejected): `RUNNING` —apply, skip, transient failure→ `RUNNING`, —data defect→ `FAILED`, —version or configuration problem, operator pause→ `PAUSED`; `FAILED`/`PAUSED` —operator resume→ `RUNNING`; `REBUILDING` is defined by the rebuild (§6.7, Phase 2). A stopped tenant shows a "data sync delayed" banner on screen (with the last sync time).
- **Schedule** (v1.4): on every 1-second tick, the scheduler walks the tenant list (dedicated role `portal_directory`, §9) and runs, one at a time, a round for each tenant whose interval has come due. A tenant whose `tenant.status` is not `ACTIVE` is not polled. An exception in one tenant stays with that tenant. The interval is `tenant.params.poll_interval_seconds` (5–30; if absent or out of range, the global default — not added to the default key set in §14.0). Polling runs only when `portal.ingest.enabled` is set (off by default — the deployment configuration turns it on). Service tokens are per-tenant files `<token-dir>/<TENANT_ID>` (if readable by anyone other than the owner, a warning on first use after startup).
- **Operator commands** (v1.4, `portal-app` `cli` profile — a single command without web or polling; exit codes 0 success, 2 rejected, 64 usage): `ingest status [--tenant=T]`, `ingest resume --tenant=T --from-seq=n` (`FAILED`/`PAUSED` → `RUNNING`, cursor set to n — n may only be at or below the last `APPLIED` seq; skipping ahead is possible only through a rebuild), `ingest pause --tenant=T` (`RUNNING` → `PAUSED(OPERATOR_PAUSE)`), `ingest rebuild --tenant=T` (v1.5 — §6.7, exit code 1 = rebuild failed). The actor is the single OS user who ran the command (`user.name`) — `--actor` was removed in v1.6 (using it yields 64). Every invocation is written to `audit_access` in the same transaction — rejections included (`INGEST_STATUS`, `INGEST_RESUME`, `INGEST_RESUME_REJECTED`, `INGEST_PAUSE`, `INGEST_PAUSE_REJECTED`, v1.5 `INGEST_REBUILD`, `INGEST_REBUILD_REJECTED`, `INGEST_REBUILD_SUCCEEDED`, `INGEST_REBUILD_FAILED` — a rebuild writes one row per transaction, `actor_subject` = `cli:<actor>`, `target` = `cursor:<T>@<n>`). Without an actor there can be no audit, so the command is rejected before it touches the DB. **Authorization** (v1.6 — portal Phase 3 D-9): `cli:<OS user>` must be present in **that tenant's** `identity_link` as `OPERATOR` and `ACTIVE` (checked first, per command and per tenant). Otherwise `T not authorized` + exit code 2, and `AUTHZ_DENIED` in that tenant (detail `NO_LINK`, `REVOKED` or `ROLE`; target `cli:ingest <command>`). A `status` without a specified tenant checks every tenant and returns 2 if any one is denied. Registration and removal are done with operations SQL (`docs/운영.md` §3). Access control on the operations host remains the first gate.

### 6.2 Status mapping (engine → screen)

| Engine fact | `earnings_month.status` | `net_basis` (v1.5) | Screen label | After-tax display |
|---|---|---|---|---|
| Lines only | PROVISIONAL | NULL | **Estimated** | "After-tax amounts shown after the overnight calculation" |
| `StatementEstimated` received | ESTIMATED | ESTIMATE | **Estimated** (shows the reference time `estimate_as_of`) | Estimated withholding tax and estimated take-home amount. The basis seq (`estimate_basis_seq`) is a lower bound — even if lines arrive after it, the status stays ESTIMATED |
| `StatementConfirmed` received | CONFIRMED | CONFIRM if tax amounts are included, otherwise the previous value is kept (ESTIMATE or NULL) | **Confirmed** — if `net_basis = ESTIMATE`, "Confirmed (after-tax estimated)" | Engine v1 does not carry tax or net amounts in close events — the estimated values are left as they are |
| `PayoutCompleted` received | PAID | PAYOUT | **Paid** (payout date) | Confirmed withholding tax and actual payout amount (`paidAmount` = `net`), confirmed payout-run values `payable`, `receivable_offset`, `carried_receivable`, `run_seq` |

State transitions follow only the order above (v1.5 — `MonthTransitions`): lines, estimates or a second confirmation for a closed month (CONFIRMED/PAID), and a second payout for PAID, are poison (`CLOSED_MONTH`); a payout before confirmation is poison (`STATE_MISMATCH`). A confirmation creates the row even for a month without lines (an agent with only corrections — engine E1 acceptance Q1). If the totals of a close or payout event differ from the portal sum, this is not poison; instead the month gets `data_status = DRIFT` + `recon_result(source EVENT)` (§6.6).

| Deferral status | Screen label |
|---|---|
| SCHEDULED | Scheduled |
| RELEASED | Payable this month |
| HELD | On hold — reason shown (e.g. the retention condition for that month is not met) |
| CANCELLED | Cancelled — reason shown (surrender, lapse, etc.) |

Deferral transitions (v1.5 — exactly the engine's `ScheduleEntry`, **DD-9**): SCHEDULED → RELEASED (`DUE_RELEASED`), HELD (`POLICY_NOT_INFORCE`) or CANCELLED (`POLICY_TERMINATED`, `SOURCE_REVERSED`); HELD → CANCELLED. **There is no HELD → RELEASED** (the engine rejects it — the diagram in the previous edition was wrong). If the stored state differs from the event's `from`, or the reason pairing is wrong, it is poison (`STATE_MISMATCH`).

If a line has a `reversal_of_calc_id`, the original line is collapsed as "Cancelled" and shown paired with the negative line. Both are included in the sums (same convention as the engine).

### 6.3 Limit view rules

- `remaining = limit_amount − accum_paid`, `utilization = accum_paid / limit_amount` (for display, one decimal place, not stored).
- Warning levels: `utilization ≥ limit_warn_pct` → WARN, `≥ limit_crit_pct` → CRITICAL, `remaining = 0` → FULL. The thresholds are tenant parameters.
- Cuts/deferrals: lines with `earnings_line.limit_cut_amt > 0` are summed per policy and shown as `cut_total` (CUT) and `deferred_total` (DEFER_AFTER_FY), and the deferred portion is linked to `deferral_item(reason=LIMIT_DEFER)`. The sums are stored on the ledger row when `CommissionCalculated` is applied (v1.5 — integer summation). **The current engine actually cuts `DEFER_AFTER_FY` as well (engine E1-1)** — `deferred_total` is merely a classification by the policy value recorded on the insurance policy, and the screen wording is decided in Phase 4.
- For policies concluded before 2026-07-01 the engine opens no ledger, so the screen shows them as "policy not subject to the limit (by contract date)". The portal does not decide this branch — it shows it only from the fact that there are no ledger events.
- The days remaining until `fy_end`, the rebase (REBASE) history and the policy status badges (`policy_flag`) are shown together.
- If a `REINSTATE` (engine REVIVE) arrives without an open flag, it is not poison; nothing is done (v1.5 — Phase 2 acceptance review Q6: it has not been confirmed whether the engine can emit a reinstatement without a lapse, so it is accepted conservatively).

### 6.4 Deferral forecast rules (the boundary of the only "calculation" the portal performs)

- Base forecast = monthly `Σ deferral_item.amount where status ∈ {SCHEDULED, RELEASED}`. HELD is shown in a separate color; CANCELLED is excluded.
- v1.5 (Phase 2): HELD never becomes RELEASED again (engine transitions — §6.2, **DD-9**) — the Phase 4 screen wording is to be chosen so that HELD amounts do not look like "money that can still be received". Items with `reason = LIMIT_DEFER` (limit deferral) are included in the same total (they follow the same state rules as `CURVE`). Phase 2 only creates `deferral_item` rows; totals and toggles come with the Phase 4 queries.
- **Scenario toggle**: "all policies retained" (default) / "assumed retention rate p%" — the monthly totals are multiplied by p for display. The feature is implemented in v1, but because **the tenant parameter `forecast_scenario_enabled` defaults to false** (confirmed, §14.0), the toggle is not exposed on screen and the API rejects `scenario=RETENTION` requests with 400. For tenants that enable it, a "not confirmed, for reference only" notice is fixed on screen. The implementation is isolated in a single class, `ForecastScenario`, and code outside this class that multiplies an amount by a ratio is forbidden by an architecture test.
- Renewal (retention) commissions have no schedule in the engine, so they are not included in the forecast. The screen carries the wording "only confirmed deferral schedules are reflected".
- Whether the 2027 deferral scheme applies to each product line (press reports suggest some products may be excluded) is decided by the engine's curve data. The portal merely shows `deferral_item` rows when they exist and shows nothing when they don't.

### 6.5 Notification rules

Notifications are derived in the **same transaction** as the projector apply, and `dedup_key` prevents duplicates. **`dedup_key` is unique per agent** (v1.2 Q1 — unique key `(tenant_id, agent_id, dedup_key)`; the key strings below do not include the agent, since the column already holds it).

| Trigger event | Condition | dedup_key | Severity |
|---|---|---|---|
| `LimitLedgerPosted` | utilization **crosses upward** a WARN/CRIT/FULL boundary | `LIMIT_{LEVEL}:{ledgerId}` | INFO/WARN/CRIT |
| `CommissionCalculated` | `limitCutAmt > 0` | `LIMIT_CUT:{calcId}` | WARN |
| `LimitLedgerRebased` | Always | `LIMIT_REBASE:{ledgerId}:{postingSeq}` | INFO |
| `CommissionCalculated` | commType=CLAWBACK | `CLAWBACK:{calcId}` | WARN |
| `DeferralStatusChanged` | to ∈ {HELD, CANCELLED} | `DEFER_{to}:{scheduleId}` | WARN |
| `PolicyEventObserved` | PAYMENT_OVERDUE/LAPSE/CANCEL | `POLICY_{type}:{policyNo}:{occurredOn}` | WARN |
| `StatementConfirmed` | Always | `STMT_CONFIRMED:{ym}` | INFO |
| `PayoutCompleted` | Always | `PAYOUT:{ym}` | INFO |
| Inquiry answered | status→ANSWERED | `INQUIRY:{inquiryId}` | INFO |

An option (tenant parameter) bundles many notifications of the same type on the same day into one daily digest. The channel in v1 is in-app; push and Kakao AlimTalk (알림톡) are added later as `NotificationChannel` adapters.

### 6.6 Reconciliation (Recon) and DRIFT

- **The first checkpoint is event reconciliation** (v1.5, Phase 2 directive D-3): when `StatementConfirmed` or `PayoutCompleted` is applied, the month's portal sums (gross/offset/adjustment) are compared with the engine totals in the event. A difference is **not poison** — the event is applied (ingestion does not stop), the month gets `data_status = DRIFT`, `recon_result` gets one row with `source = EVENT` and `source_seq` = the event's `seq` (`ON CONFLICT DO NOTHING` — even if a rebuild or re-apply inserts the same row again, there is one row; approval Q2), and the metric `projection_drift_total`. Total fields absent from the event are not compared (the engine fields are optional — contract v1.1). If they match, no row is written. `engine_net` is the event's `net` (NULL if absent). A month that has once become `DRIFT` stays `DRIFT` even if later events match (recovery is through the rebuild and re-reconciliation below).
- The nightly reconciliation below is added in Phase 7 with `source = BATCH` (`source_seq` = 0).
- Every day in the early morning, per tenant and for all active agents, the current-month (ESTIMATE) and previous closed-month (CONFIRMED) statements are fetched page by page from the engine and compared with the portal sums (gross/offset/adjustment) in `earnings_month`.
- Mismatch → `recon_result=DRIFT`, `earnings_month.data_status=DRIFT`. The screen puts a "Verifying" badge on that month's card and shows the amounts **dimmed** (numbers that may be wrong are not shown as if they were confirmed).
- Automatic recovery (Phase 7): rebuild the tenant (§6.7) → re-reconcile → `REPAIRED` if they match. If they still do not match, an operations alert (the engine side may have made a correction). The rebuild is atomic (if it fails, nothing changes), so no shadow schema or swap is needed (v1.5).

### 6.7 Rebuild

v1.5 (Phase 2 directive D-11, plan ⑥, approval Q10): **a single-transaction replay, not a shadow schema.** The view tables are shared by tenants (multi-tenant), so a `RENAME` swap is impossible, and PostgreSQL MVCC already provides "queries keep seeing the existing rows during a rebuild". With no DDL, there is no FORCE RLS or `portal_migrator` problem either. `ingest rebuild --tenant=T` (§6.1 operator commands) consists of three transactions — the directive's "cursor `REBUILDING` → replay inside one transaction" would make the state merely decorative, since a `REBUILDING` set in the same transaction is invisible to everyone until commit (**DE-9**).

1. **txA**: tenant lock (`pg_try_advisory_xact_lock` — same key as the worker; if it cannot be acquired, reject and audit) → check that the cursor state accepts `OPERATOR_REBUILD` (only `RUNNING`, `PAUSED`, `FAILED`) → commit `REBUILDING`. From then on the worker does not poll (the applier also sees `NOT_RUNNING` inside its transaction), and `resume`, `pause` and `rebuild` are rejected.
2. If the `headSeq` of the first feed page (`afterSeq=0`) > `portal.ingest.rebuild.max-events` (default 50,000), the rebuild does not start; go to txC.
3. **txB** (one transaction — atomicity): lock (retried briefly, because the apply transaction of a worker that read `RUNNING` just before txA may hold it for a moment) → DELETE from the 8 view tables (`agent_profile`, `earnings_line`, `earnings_month`, `policy_limit`, `policy_limit_history`, `deferral_item`, `clawback_item`, `policy_flag`) → replay from seq 1 up to the `headSeq` from step 2 (a head that grows during the replay is not followed — this is the premise for the cap bounding the length of txB; the rest is picked up by the worker from the cursor once it is `RUNNING`. If the replay count reaches the cap, it fails — a double safeguard. If that `headSeq` is lower than the highest seq already `APPLIED`, it fails before deleting, and it also fails if the feed ends before reaching that seq — if the cursor stood below `APPLIED` rows, the worker would skip them as known events and they would be missing from the views forever) — with the **same checks** as the applier (`EventCheck`: receive validation, hash, comparison with existing `APPLIED` rows, seq rule); a row already `APPLIED` must have the same hash and seq, and if it does, the projector is called again; if it is absent or `FAILED`, it becomes `APPLIED` → full invariant check (month total = Σ lines — including lines that have no month row; ledger accumulation = Σ postings; posting chain `accum_after − previous = amount`) → cursor `RUNNING`, `last_seq` and `last_event_id` = the last replayed event → commit.
4. Failure (poison, hash mismatch, seq discontinuity, invariant, feed error, cap): txB is rolled back (views, `ingest_event` and `recon_result` all return to their original state) → **txC** sets the cursor to `FAILED(REBUILD_FAILED: {cause} seq n)`, leaving `last_seq` as is. After the cause is fixed, the rebuild can be run again from `FAILED`.

- **Not deleted**: `ingest_event` (the reference for hash comparison), `ingest_cursor`, `audit_access`, `recon_result` (DRIFT history — even if the replay recreates the same row, `ON CONFLICT DO NOTHING`), and user data (`tenant`, `identity_link`, the read state of `notification`, `inquiry`, `simulation_log`).
- **Stream source**: re-fetched from the feed — the portal does not store payloads (Phase 1 D-1). The feed calls are inside txB (a long transaction — §14.1 #11).
- Notification derivation (§6.5) is not invoked during replay — to be decided in Phase 6 (§14.1 #12).
- Audit: start, rejection, success and failure, one row each inside the transaction of the respective step. Metric `ingest_rebuild_seconds{outcome}`.
- **Determinism** (D-12): from the same stream, the 9 view tables (including `recon_result`, excluding the tenant column, including `source_seq` and `last_seq`), `ingest_event` and `ingest_cursor` are byte-identical across ① a single pass, ② a restart midway, and ③ a single pass + rebuild (`IngestDeterminismIT`).

---

## 7. Query API (portal → web)

Self endpoint paths contain **no** `agentId`. The agent is identified only by the token subject → `identity_link` lookup, which structurally eliminates IDOR. **Every statement** in the query repository (`AgentQueryRepository`) carries `agent_id = :agentId` (including each table in a join — the second predicate of the SQL scan). The canonical contract is `contracts/api/v1/portal-api.openapi.yaml` (v1.6 — owned by the portal; web Phase 4 generates the client), and every test response is validated against that contract.

| Method/path | Response summary |
|---|---|
| `GET /api/v1/me` | Own ID and role (`identity_link`), name, grade, organization and status (`agent_profile` — **the agent's own name is not masked**; omitted if there is no row — v1.6 DD-13), tenant display parameters (name, payout-day rule, warning thresholds in bp) |
| `GET /api/v1/me/earnings?from=YYYYMM&to=YYYYMM` | Monthly summary time series (gross/clawbackOffset/adjustment/withholdingTax/net/netBasis/paidAmount/paidAt/status/monthDataStatus). `from` and `to` required, `from ≤ to`, span of at most 36 months (otherwise 400) |
| `GET /api/v1/me/earnings/{ym}` | Month summary + totals by type (`byType` — SQL integer sums) + confirmed statement and payout values + expected payout date (tenant rule `NEXT_MONTH_D{n}`; omitted with a warning if n does not exist in that month or the format differs — §14.1 #2) |
| `GET /api/v1/me/earnings/{ym}/lines?page&size&commType&policyKey` | Line list — **flat** (v1.6); reversal pairs are linked by `reversalOf`/`reversedBy`, and folding is done by the screen (Phase 4). Sorted by `source_seq, calc_id`, `page` starts at 0, `size` 1–200 (default 50). Filter `policyKey` (not the policy number — see below) |
| `GET /api/v1/me/earnings/lines/{calcId}` | Line detail: basis (basis — the opaque string as is), limit decision (limitCut), policy summary, this line's deferrals and receivables, the limit ledger ID of that policy (inquiry button is Phase 6) |
| `GET /api/v1/me/limits?status=WARN\|CRIT\|FULL\|ALL` | List of per-policy limit cards (remaining limit = SQL integer subtraction, `utilization` display string — omitted when the limit is 0, stage = integer comparison against the tenant thresholds in bp, open flags) |
| `GET /api/v1/me/limits/{ledgerId}` | Limit detail + history timeline (in `source_seq` order) |
| `GET /api/v1/me/deferrals?from&to` | Sums by expected payout month (scheduled = SCHEDULED+RELEASED, held = HELD — SQL integer sums) + items. Scenario toggle (`scenario=ALL_INFORCE\|RETENTION&p=`) is Phase 5 |
| `POST /api/v1/me/simulations/new-contract` / `/policy-event` | Engine result + simId (log reference) |
| `GET /api/v1/me/notifications?unreadOnly` / `PATCH .../{id}/read` | Notification inbox |
| `POST /api/v1/me/inquiries` / `GET /api/v1/me/inquiries` | Disputes |
| `GET /api/v1/org/…` | v2. Org manager's view of subordinates. Authorized by `org_path` prefix match; access is recorded in `audit_access` |

Common fields in every response: `asOf: {seq, at}` (cursor `last_seq` and `updated_at` — with no cursor, `seq` is 0 and `at` is omitted), `dataStatus: OK|SYNC_DELAYED` (**sync** status — delayed if any of: cursor state ≠ `RUNNING`, no `caught_up_at`, `now − caught_up_at > sync_delay_seconds`; also delayed during a rebuild). Month responses give that month's **reconciliation** status separately as `monthDataStatus: OK|DRIFT` — up to v1.5 a single field `dataStatus: OK|DRIFT|SYNC_DELAYED` mixed the two meanings (v1.6 **DD-12**). Amounts are integer won; currency formatting is the frontend's job. Optional fields are left out when they have no value (`null` is not sent).

**The policy number is in no response** (v1.6): `policyMasked` (`*` except the last 4 characters) and `policyKey` = the first 16 hex characters of HMAC-SHA256 (deployment secret file `portal.query.policy-key-secret-file`, `tenant_id` + `U+001F` + `policy_no`). A hash without a secret can be reversed by enumeration because the policy number format is narrow (**DE-15**). Key → original is resolved within the request only against **that agent's** set of policy numbers (no key in SQL — another agent's key yields an empty result). Rotating the secret changes every key — the screen does not store keys (`docs/운영.md`). The insured is the engine-masked `insuredMasked` as is.

**Rejections and formats** (v1.6, §9): every authentication failure is the same 401; no link, `REVOKED`, role (`MANAGER`, `OPERATOR`), another agent's (or a nonexistent) resource, nonexistent path, method mismatch and path variable format errors (`ym`, key) are all the same 404; only query parameter format and range errors are 400 `INVALID_REQUEST`. The body is `application/problem+json` `{code, message}`.

**Caching** (v1.6, §10): 200 carries `Cache-Control: private, no-cache` (not `no-store` — so that 304 means something) and a weak ETag `W/"<seq>.<sync>.<cfg>"` (`sync` = `O`/`D`, `cfg` = the first 8 characters of the SHA-256 of the tenant settings that response actually read — payout-day rule, thresholds, delay seconds; the line list appends `.<page>.<size>.<8-char filter hash>`). Even at the same `seq`, the ETag changes when the delay status or settings change (**DE-16**). `If-None-Match` is compared only after the ownership check (another agent's resource gets 404, not 304).

**Response example — `GET /api/v1/me/earnings/202609`**

```json
{
  "asOf": { "seq": 10231, "at": "2026-09-22T01:15:41Z" }, "dataStatus": "OK",
  "closeYm": "202609", "status": "ESTIMATED", "monthDataStatus": "OK",
  "gross": 4130000, "clawbackOffset": -350000, "adjustment": 0,
  "withholdingTax": 124740, "net": 3655260, "netBasis": "ESTIMATE",
  "byType": { "CLAWBACK": -350000, "FY_COMM": 2800000, "INCENTIVE": 800000, "RENEWAL": 530000 },
  "estimateAsOf": "2026-09-21T17:10:00Z", "estimateBasisSeq": 10198,
  "payoutExpectedOn": "2026-10-10"
}
```
(Amounts and tax rate are example values. Tax amount and net amount come from the engine's `StatementEstimated`; the portal does not calculate them.)

---

## 8. Screen design (mobile first, text wireframes)

**Common**: a sync status chip at the top (`As of 09-22 10:15` / `Sync delayed` / `Verifying`). A status label next to every amount (Estimated · Confirmed · Paid). Integer won, with thousands separators.

1. **Home**
   - This month's card: expected/confirmed take-home pay (large number) · pre-tax · clawback offset · withholding tax · expected payout date D-n · status label
   - One line comparing with the previous month (on a confirmed basis)
   - Policies needing attention: N with limit WARN or above, M with late payment/lapse warnings → go to Limit status
   - One line with next month's expected deferral amount → go to the timeline
   - 3 most recent notifications
2. **Limit status**
   - Filter (attention/all) · policy card: product name (if absent, product code `productKey` — the engine has no policy master, v1.3) · insured (masked; omitted if absent) · contract date · utilization bar (%) · remaining amount · first-year end D-n · policy status badge
   - Detail: limit formula (monthly premium × multiplier, regulation reference date) · ledger timeline (postings, rebases) · cut/deferral breakdown (linked deferral items) · "Impact if this policy is surrendered" button (→ simulation)
3. **Deferral timeline**
   - Period toggle (12/24/48/84 months) · monthly bars (split into scheduled/due for payout/held) · scenario toggle (all policies in force / retention rate assumption) · policy filter
   - Fixed footer text: "Reflects confirmed deferral schedules only · For reference · Not final"
4. **Simulation**
   - Tab ① New-policy estimate: product selection · monthly premium · contract date (default today) → expected amount per first-year installment · limit and expected utilization · whether incentives are included · deferral schedule for the first year/after
   - Tab ② Surrender impact: policy selection · point in time → clawback amount · change in this month's take-home pay (before/after) · deferral items that get canceled · receivable balance
   - Fixed text: "For agent reference only · Not final · Not to be shown to customers" · the rule snapshot ID is shown on results
5. **Monthly statement**
   - Month selection · totals by type → line list (policy · type · amount · status · reversal pair) → line detail (applied rate · payout rate · installment number — only what the engine provided; if absent, that row is omitted (v1.3), limit decision, linked items) → "Ask about this item"
6. **Notification inbox / settings**: mark as read, digest settings (if the tenant allows).

Accessibility and field use: minimum touch target 44px, large numbers first, low-end devices considered (bundle size cap checked in CI), no screen-capture prevention (it is the agent's own data).

---

## 9. Security, privacy and multitenancy

- **Authentication**: OIDC. A per-tenant IdP (the adopting GA's SSO) or the portal's default IdP. Development and demo use Keycloak (Phase 8); tests use an in-process issuer (RSA key + JWKS stub). **The tenant is determined by the token's `iss`** (v1.6): `tenant.oidc_issuer` (UNIQUE, exact match) and `oidc_audience` — if both are NULL the tenant cannot log in. The issuer table must be read **before** binding the tenant, so it is read on every request with the dedicated role `portal_issuer` (role table below) (no cache — revocation takes effect immediately). `/api/**` verification order (portal Phase 3 plan ②; all yield **the same 401 bytes** `{"code":"UNAUTHENTICATED","message":"authentication required"}` + `WWW-Authenticate: Bearer` — the reason goes only to metrics): ① exactly one `Authorization: Bearer` header (none, another scheme, or more than one → `NO_TOKEN`) ② JWT format, string `iss` and `sub` (1–200 characters) → `MALFORMED` ③ header `alg` = `RS256` only → `ALG_NOT_ALLOWED` ④ `iss` → registered tenant → `ISSUER_UNKNOWN` (only registered issuers get their metadata and JWKS fetched — no outbound request to a URL chosen by the token) ⑤ signature against that issuer's JWKS (one decoder per issuer URL) → `SIGNATURE` ⑥ `exp` (required) and `nbf`, 60-second tolerance, injected clock → `EXPIRED` ⑦ `aud` contains that tenant's `oidc_audience` → `AUDIENCE` ⑧ **if** a `tenant_id` claim is present, it is the same tenant (cross-check — not a basis of trust) → `TENANT_MISMATCH`. If the JWKS cannot be fetched, the result is 500, not 401 (a server-side failure is not hidden by blaming the token). The only things read from the token are `iss`, `sub`, `aud`, `exp`/`nbf` and `tenant_id`; the `roles` and `agent_id` claims are read nowhere — the only portal class that touches `Jwt` is the verifier. Roles (team leader, branch manager, etc.) are not taken from engine events either — the engine has no concept of roles (override recipients are organizations, not people), and `AgentSnapshot.role` is optional (v1.3).
- **Authorization**: the caller (`Caller` = tenant, `agent_id`, role) is built only from a single `identity_link` PK lookup (no cache) (v1.6). Roles are the closed set `AGENT` (agent's own data), `MANAGER` (organization view — v2; for now rejected on `/api/v1/me/**`) and `OPERATOR` (subject of the operations CLI — no `agent_id`, rejected on `/api`). No link, `REVOKED`, disallowed role, and another agent's (or a nonexistent) resource are all **the same 404 bytes** `{"code":"NOT_FOUND","message":"not found"}`, and the reason goes only to the audit (`audit_access` action `AUTHZ_DENIED`, `target` = path template, `detail` = closed reason code `NO_LINK`, `REVOKED`, `ROLE`, `MISSING` — no values, **in a transaction separate from the request itself**) and to metrics. Nonexistent paths under `/api`, method mismatches and path variable format errors are the same 404 too (no audit); only query parameter format errors give 400 `INVALID_REQUEST`, the single exception. A 401 has no audit row — a request whose tenant is unknown has nowhere to write a row, and forged tokens carrying only a registered `iss` could fill the audit table (write amplification). Self endpoints query only by the `Caller`'s `agent_id`. The organization view (v2) uses org path prefix match + role check, with every access recorded in `audit_access`. **The org path has two sources** (v1.6): `agent_profile.org_path` is an engine fact (`AgentSnapshot` — for display) and `identity_link.org_path` is a portal fact (registered by operations — for authorization, the scope of the v2 organization view). Even when they differ, neither is corrected (which one to follow is a v2 decision — §14.1 #9).
- **Three-layer tenant isolation**: ① a request filter binds `TenantContext` to the tenant of the verified token issuer (v1.6); without authentication, 401 ② the repository layer allows only `TenantScopedRepository` — architecture tests (ArchUnit + SQL source scan) forbid queries without a `tenant_id` condition ③ PostgreSQL RLS. `TenantContext`, `TenantSessionBinder` and `TenantScopedRepository` come from platform 0.2.0 (v1.2); rejecting DB access outside a transaction and failing to start a transaction without a context are guarantees of the platform, but portal tests (`TenantContextGuardTest`, `TenantSessionBinderTest`) prove them again. Each entry on the direct-access allowlist must satisfy all three conditions — closed FQN list + dedicated role + no access to tenant data — and the list was empty in Phases 0–2. The first entry, in v1.6 (portal Phase 3), is `com.ga.portal.infra.directory.IssuerDirectoryReader` — the issuer table is tenant configuration and must be read before the tenant is bound (dedicated role `portal_issuer`, a small read-only connection pool for that reader alone).
- **Architecture rules** (v1.2, `portal-app` `archTest` — rule building blocks are platform-core `ArchRules`): (a) dependency direction `app → query/ingest/sim/recon → projection → domain → platform-core`; `infra` and `demo` only from `app` (b) `domain` and `projection` (and platform-core) have no dependency on Spring, `java.sql` or `javax.sql`; of the platform, only core (c) `double`/`float` forbidden in every module (d) DB access only by `TenantScopedRepository` subclasses; raw entry points (`DataSource`, `JdbcClient`, `JdbcTemplate`, …) only by platform classes and the portal allowlist (e) `platform-spring` only from `infra` and `app` (f) 0 portal classes sharing a simple name with a platform-core type (g) connection- and statement-level JDBC types (the `DataSource`, `DriverManager`, `Connection`, `Statement` families) only in platform classes and the allowlist — the result type (`ResultSet`) is allowed by (d) down to repository subclasses because of `RowMapper` (h) `BigDecimal` forbidden in every portal module. The SQL scan (`TenantPredicateScanTest`) requires, in the main sources of every module — DML in migration `.sql` files and SQL in Java strings — a `tenant_id` condition on every access to a tenant table (including `tenant`) except `flyway_schema_history`, and for Java repository SQL it also requires the `:tenantId` bind (for INSERT, the value in the `tenant_id` position; otherwise `tenant_id = :tenantId`). The allowlist is (file, statement) pairs, each entry with a reason, and it fails if a retired entry remains — the only entry in v1.6 is the issuer reader's `iss` lookup statement (paired with the direct-access allowlist entry above).
- **Query API rules** (v1.6, portal Phase 3): `portal-query` is on the Spring/DB-free list of (b) (both Gradle `verifySpringFree` and ArchUnit). **Second predicate** — every statement of the query repository (closed list: `AgentQueryRepository`) carries `agent_id = :agentId` as many times as there are `FROM`/`JOIN` references to tables with an agent column (including subqueries and joins; allowlist empty). `ApiLayerRulesTest`: ⓐ every `@RestController` handler method takes a `Caller` and calls exactly one `@UseCaseEntry` method ⓑ the classes that see `Jwt` are a closed list (the one verifier), nobody sees `JwtAuthenticationToken`, and controllers do not see Spring Security or `Principal` ⓒ in `portal-query`, the only public methods taking a `Caller` are `@UseCaseEntry` ones (closed exception: `QueryAuthorization.require`) ⓓ every entry point calls `QueryAuthorization.require` ⓔ only `CallerArgumentResolver` calls the `Caller` constructor ⓕ the only view repositories reachable from query paths are `AgentQueryRepository` and `SyncStatusRepository`. The canonical authorization matrix is `docs/authz-matrix.md` — `AuthzMatrixTest` cross-checks document ↔ `QueryAuthorization` ↔ `@GetMapping` ↔ `@UseCaseEntry` in both directions.
- **DB roles** (v1.2, canonical `docker/postgres/init-roles.sql` — every role is `NOBYPASSRLS` and `NOSUPERUSER`, asserted by V2):

  | Role | Purpose | Privileges (GRANT) | Used by |
  |---|---|---|---|
  | `portal_migrator` | Schema owner, migrations | Owns the DB and the `public` schema. Because of `FORCE RLS`, the owner also follows the policies | Flyway (`portal-app` startup, test harness) |
  | `portal_app` | Application data source | `SELECT/INSERT/UPDATE/DELETE` on 17 tenant tables, `USAGE` on `audit_access_id_seq`, schema `USAGE`, DB `CONNECT`. `NOINHERIT`; no `TRUNCATE`, `TRIGGER`, `REFERENCES` or `CREATE`; no privileges on `flyway_schema_history` | `portal-app` (repositories under platform-spring `TenantSessionBinder`) |
  | `portal_directory` | (v1.4, V3) Tenant list only — the tenants the ingest worker runs for | Only **column** SELECT on `tenant.tenant_id` and the policy `tenant_directory` dedicated to this role (SELECT, `USING (true)`). 0 privileges on other columns or tables and no write privileges, `NOINHERIT` | Platform `TenantDirectoryReader` (`portal-app` worker). Tenant data is read per tenant by binding with `portal_app` |
  | `portal_issuer` | (v1.6, V5) Issuer table only — token `iss` → tenant | Only **column** SELECT on `tenant`'s `tenant_id`, `oidc_issuer` and `oidc_audience` and the policy `tenant_issuer` dedicated to this role (SELECT, `USING (true)`). 0 privileges on other columns or tables and no write privileges, `NOINHERIT` | Portal `IssuerDirectoryReader` (`portal-app` security chain — direct-access allowlist entry) |
  | `portal_health` | (Reserved — portal Phase 8) DB health indicator only | 0 privileges on tenant tables | In Phase 0 the DB health indicator is disabled (`management.health.db.enabled=false`) — it is not turned on without an allowlist entry and a dedicated role |
- **Privacy minimization**: the portal stores customer names only in masked form (the engine masks them before publishing) and does not receive resident registration numbers, contact details or addresses. Policy numbers are stored, but **query responses contain no original** (v1.6 — `policyMasked` last 4 characters, `policyKey` HMAC, §7). Logs and metrics carry subject, `agent_id` and issuer URL only as hashes (first 12 characters of SHA-256), and the only metric tags are `tenant` and `reason`. Storage encryption (disk, backups), TLS in transit.
- **Retention**: notifications 1 year, simulation logs 3 years (example value, tenant parameter), audit log 5 years (example value). Lines and statements depend on the engine's retention policy (since they can be rebuilt, the portal's own retention can be reduced to an operationally convenient period).
- **Service-to-service authentication**: portal → engine uses a service token (mTLS in operation). The engine's `/internal/**` is reachable only from the portal network.
- **Rate limiting**: simulations and on-demand statement refreshes are limited to N per minute per agent (parameter). When exceeded, the last result is shown again.

---

## 10. Non-functional requirements and operations

| Item | Target | Means |
|---|---|---|
| Projection lag | Engine commit → screen within 60 seconds at p95 | Poller interval parameter (`tenant.params.poll_interval_seconds` 5–30, otherwise the global default of 10 seconds — v1.4), metrics (Micrometer, tag `tenant`): `ingest_lag_seq` (= `headSeq − last_seq`), `ingest_lag_seconds` (seconds elapsed since the last cursor update while behind; 0 once caught up), `ingest_state` (0/1 per state), `ingest_events_total{result=applied\|skipped\|failed}`, `ingest_feed_errors_total`, v1.5 `projection_drift_total` (§6.6) and `ingest_rebuild_seconds{outcome}` (§6.7) — dashboards and alerts are Phase 7 |
| Peak load | 09:00 on the day after close, all agents connecting at once | The SQL statement count of the three home calls is independent of data volume (v1.6 — `/me` 4, `/earnings/{ym}` 5, `/limits?status=WARN` 4 = issuer 1 + link 1 + settings/cursor 1 + body; the same with 1,000 lines and 200 ledgers — `QueryStatementIT`). 304 via a weak response ETag (`seq`, sync status, fingerprint of the settings read — §7), `Cache-Control: private, no-cache` |
| Availability | Queries continue while the engine is down | D-3. Only simulations go into degraded mode |
| Consistency | Portal totals = engine statement (verified daily) | §6.6 reconciliation, `recon_drift_count` metric |
| Recovery | Rebuild possible from any state | §6.7, determinism tests |
| Observability | Per-tenant dashboards | Structured logs (tenant_id, agent_id hash), OpenTelemetry traces (including portal → engine calls). Query API (v1.6): `api_authn_rejected_total{tenant, reason}` (the eight 401 reasons — `tenant` is that tenant only when the issuer is registered, otherwise `none`: the set of tag keys is always the same) and `api_authz_denied_total{tenant, reason}` (404 — `NO_LINK`, `REVOKED`, `ROLE`, `MISSING`). No agent or subject tags. `sub`, `agent_id` and issuer URL in logs are the first 12 characters of SHA-256 (`LogIds`) |
| Deployment | Containers, zero downtime | Principle of forward-compatible Flyway migrations (column drops in two steps) |

---

## 11. Tech stack

| Area | Choice | Notes |
|---|---|---|
| Backend | Java 25 (LTS) + Spring Boot 4.1.x | Same line as the engine. If the engine is on the 3.5 line, the version mismatch is not a problem since it is a separate process. Boot 4 requires Java 17+ |
| Persistence | PostgreSQL 18.x (image digest pinned — `postgres-image` in `gradle/libs.versions.toml`, starting value 18.6) + Flyway + repositories are only subclasses of platform-spring `TenantScopedRepository` (a `JdbcClient` wrapper) | RLS, JSONB. Projectors have no DB dependency (tested with in-memory repositories). Spring Data JDBC repository interfaces are not used because they bypass the path that enforces the tenant predicate (v1.2) |
| Frontend | React 19 + TypeScript + Vite, PWA | One lightweight charting library; state centered on a server-state cache (TanStack Query, etc.) |
| Authentication | OIDC / Keycloak (development) | Spring Security resource server |
| Testing | JUnit (version managed by the Boot 4.1 BOM — JUnit 6), AssertJ, Testcontainers (PostgreSQL), ArchUnit / Vitest, Playwright. **jqwik forbidden** (blocked at Gradle resolution) — property tests use `@ParameterizedTest` + `SeededCases` (`platform-core` test fixtures) | §13 |
| Contracts | JSON Schema (2020-12) + OpenAPI 3.1 | `contracts/`, validated in the CI of both repositories. `format` forbidden (`pattern`); the shared core is a copy under `contracts/shared/ga-disclosure/` (§4 contract conventions, v1.2) |
| Build and run | Gradle multi-module (version catalog + dependency locks + verification metadata — SHA-256 for every dependency including plugins and BOMs, SNAPSHOT forbidden), pnpm, docker compose (engine+Oracle, portal+PostgreSQL, keycloak, demo seed) | Full verification with two commands: `./gradlew build` + `pnpm build` |
| Platform | `com.ga.platform:platform-core`, `platform-spring`, `platform-canonical` **0.2.0** (published by ga-disclosure, GitHub Packages) | v1.2. 0.1.0 is not used. CI resolves only from GitHub Packages (PAT secrets `GHP_USER`, `GHP_READ_TOKEN`), local builds only from `mavenLocal`, and the SHA-256 of 13 jars, POMs and module metadata files is pinned in the verification metadata (`verifyPlatformPins` cross-checks against the table in §6 of the ga-disclosure handoff) |

---

## 12. Implementation roadmap (Claude Code work order)

Each Phase must be independently mergeable and proves its completion criteria with tests. `E-` denotes work in the engine repository.

- **Phase 0 — Skeleton**: repository, Gradle modules, Flyway, Testcontainers (PostgreSQL), `Won`/`Ym`/`TenantId` value objects, `TenantContext`, RLS session binding, architecture tests, initial schemas under `contracts/`. *Completion criteria: a repository method without a tenant condition fails in tests; 0 rows in a session without RLS set.*
- **Phase E1 — Engine outbox and feed**: §4.4 items 1, 2, 5, 6 + the event feed of 4 (v1.3 — moved forward from E2 so that portal Phase 1 does not end without a real supplier). *Completion criteria: when the engine golden scenario runs, the published event sequence matches the snapshot file and passes schema validation.*
- **Phase 1 — Ingestion**: poller, cursor, idempotency, seq rules, poison event isolation, reprocessing command. *Completion criteria: duplicate/out-of-order/gap/restart/cross-tenant isolation tests pass.* (v1.4: in parallel with engine E1 — the feed is tested with a stub server and a file adapter; one E2E check against the real engine after E1 acceptance)
- **Phase 2 — Projection**: earnings, limit, deferral, clawback and flag projectors, invariants, rebuild, determinism. *Completion criteria: Appendix A scenario event stream → view golden tests; two replays identical.*
- **Phase 3 — Query API, authentication, authorization**: `/api/v1/me/**`, `identity_link`, IDOR tests (querying another agent's calcId gives 404), audit log. *Completion criteria: all authorization matrix tests pass.*
- **Phase 4 — Frontend, first pass**: home, monthly statement, limit status, notification inbox, sync status chip, status label conventions. *Completion criteria: Playwright scenario (shows the Estimated → Confirmed → Paid transitions).*
- **Phase E2 — Engine synchronous API**: §4.4 items 3 and 4 (nightly `StatementEstimated`, two simulation dry-runs, statement query — the feed is E1). *Completion criteria: a test shows that a dry-run leaves nothing in the ledger or COMM_CALC.*
- **Phase 5 — Simulation and deferral timeline**: `portal-sim`, `simulation_log`, scenario toggle (`ForecastScenario` isolation tests). *Completion criteria: contract tests against engine response stubs, rendering tests for the fixed text.*
- **Phase 6 — Notification derivation and disputes**: §6.5 rules, dedup, digest, inquiry API. *Completion criteria: fires only once when crossing a boundary upward; downward and re-upward cases.*
- **Phase 7 — Reconciliation, DRIFT, observability**: §6.6, metrics and traces, operational alerts. *Completion criteria: artificially injected missing line → DRIFT detected → automatic rebuild → REPAIRED.*
- **Phase 8 — Demo tenant and integrated run**: Appendix B generator, docker compose, README (portfolio perspective), E2E. *Completion criteria: after `docker compose up`, logging in as a demo agent shows data on all 5 screens.*
- **Phase 9 (follow-up)** — org manager view, PDF statements, push/KakaoTalk AlimTalk notifications, offline snapshots.

When instructing Claude Code, every Phase prompt must include: ① the relevant sections of this design document, ② the conventions "the portal does not calculate amounts (only sums and ratio display), double forbidden, no access without tenant_id, agent_id resolved only via identity_link, no changes to closed-month views", ③ that Phase's golden cases.

---

## 13. Test strategy

| Layer | Target | Approach |
|---|---|---|
| Contract | Event and API schemas | Engine: schema validation of published events (provider). Portal: schema-based fake event generator (consumer). CI byte-compares only the shared contract copy (`contracts/shared/`) against the upstream merge commit (`verifyUpstreamContract`); checksums are plain SHA-256 (`verifyContractChecksums`) — v1.2. Portal: `EventSchemaTest` (17 samples, required-field removal, Envelope ⊇ core, pairs, 0 `format`) |
| Projection | Event stream → view | Golden streams (Appendix A) + property tests (`@ParameterizedTest` + seed-fixed generator `SeededCases`, jqwik forbidden): invariants of §5 hold for arbitrary streams, view unchanged after duplicate insertion, replay determinism |
| Ingestion | Ordering, idempotency, isolation | seq gaps/out-of-order/duplicates, other tenants proceed after a poison event, cursor consistency after restart |
| Authorization and isolation | Tenant and agent boundaries | RLS integration tests (Testcontainers), IDOR tests, ArchUnit and SQL scan. v1.6: authorization matrix ↔ code in both directions (`AuthzMatrixTest` — canonical `docs/authz-matrix.md`), byte-identical 401s and 404s (`UniformRejectionIT`, `AuthzIT`), response scan (all endpoint responses ∩ seeded policy numbers and other agents' IDs = ∅ — `ResponseLeakScanIT`), contract validation of every test response (`portal-api.openapi.yaml`) |
| Reconciliation | DRIFT detection and recovery | Scenarios injecting missing and duplicated lines |
| Frontend | Status labels, fixed text, accessibility | Vitest component tests, Playwright E2E (demo tenant), bundle size cap |
| Performance | Concentrated access | k6 scenario for the day after close (N agents × 3 home calls), checking p95 |

---

## 14. Decision record and open questions

### 14.0 Settled items (2026-09-22)

| Item | Decision | Where reflected |
|---|---|---|
| Exposure of individual team members' lines in the org manager view | GA policies differ, so a **tenant flag** `org_member_lines_visible` (default false) | §5 `tenant.params`, v2 organization API authorization policy |
| Rate information visible to agents | **Actual figures exposed as the line basis** (applied rate, payout rate, installment number) — **shown when present** (v1.3: for flat and lump-sum payouts the engine has no rate or installment number). Rate tables and other people's information are not exposed | §2.3, §7 line detail, `earnings_line.basis` |
| `StatementEstimated` production cycle | **Once nightly**. The cycle is an engine batch parameter so it can be changed later | §4.4-3, §6.2 |
| Retention rate assumption scenario toggle | **Provided by default but disabled by default** `forecast_scenario_enabled` (default false) | §5 `tenant.params`, §6.4, §7 `scenario` parameter |

### 14.1 Open questions (confirm before implementation; all absorbed by parameters and adapters)

1. Whether on-demand provisional statement refreshes are allowed, and the per-agent limit (additional triggers beyond the nightly run).
2. The actual per-tenant values of the payout-day rule (`payout_day_rule`), and whether business-day adjustment applies. (v1.6 default assumption: only `NEXT_MONTH_D{n}` is interpreted, with no adjustment. If `n` is a day that does not exist in the next month (e.g. February 31) or the format differs, `payoutExpectedOn` is **omitted** and a warning is logged — it is not pulled back to the last day of the month (plan approval Q12). Business-day adjustment and other formats remain open.)
3. Actual limit warning thresholds, digest bundling policy.
4. `OUTBOX_EVENT` retention policy and when to introduce `TenantSnapshot`.
5. Per-tenant differences in masking rules (customer name, policy number); masking at engine publish time vs. at portal display time. (v1.6 default assumption: the customer name is the engine-masked `insuredMasked` as is, and the policy number is masked by the portal at response time as `policyMasked` (last 4 characters) — no per-tenant differences. Per-tenant rules remain open.)
6. IdP integration approach (adopting GA's SSO vs. portal IdP), the subject remapping procedure for agents with multiple affiliations or who move. (v1.6 default assumption: tenant = token `iss`, one issuer per tenant, RS256. When a subject changes, the link for the old subject is set to `REVOKED` and a row for the new subject is added — `docs/운영.md` §2. The remapping procedure itself remains open. If one subject exists in two tenants, the issuers differ so each is handled separately — a configuration in which two tenants use the same issuer is not supported (`oidc_issuer` UNIQUE).)
7. Simulation rate limit values, the source of the product list (whether an engine product master API is needed).
8. Minimum requirements for a **legacy adapter** for GAs with their own settlement system (which events are mandatory — whether it can run in a reduced mode with at least `CommissionCalculated`, `StatementConfirmed` and `PayoutCompleted`).
9. **Org manager v2** (v1.5 — portal Phase 1 and E1 acceptance review §3-1): a new event carrying organization moves and org manager assignments is recommended; not decided now. Until then, organization information lives in `identity_link` (Phase 3). (v1.6: `identity_link.org_path` — a portal fact, for authorization, required except for `OPERATOR`, registered via operations SQL. `agent_profile.org_path` is an engine fact, for display (the organization in `/me`). **The two sources may differ and neither is corrected** — which one is canonical for the org manager scope is a v2 decision. The second edition of A-1 has no `AgentSnapshot`, so a tenant with an empty `agent_profile` is normal (**DE-13**).)
10. **#4 extension — current-state snapshot bootstrap** (v1.5 — E1 acceptance review §2 §9-5): ledgers from before E1 have no opening events. An engine current-state snapshot job (agents, ledgers, deferrals, receivables) is a candidate for E2 scope — the portal-side consumption (whether to take the snapshot as seq 0 facts) after seeing that plan.
11. **Cost of the long rebuild transaction** (v1.5 — §6.7): txB holds the replay of the whole tenant and the feed calls — meanwhile its snapshot is held, so VACUUM on other tables cannot reclaim dead tuples (bloat), and the deleted view rows and new rows go to WAL all at once. The only locks are that tenant's advisory lock and row locks, so other tenants and queries are not blocked. The cap (`rebuild.max-events`) bounds the size. Tenants above 50,000 need the snapshot bootstrap (#10) or page-wise shadow tables — after measurement (Phase 7 load test).
12. **Notification derivation during replay** and the **screen convention for unpaid corrections** (v1.5): replay does not derive notifications — **default direction** (Phase 2 acceptance review F9): a notification signals a new fact, and a rebuild rewrites facts. Phase 6 settles it. How the screen shows a month in which an `ADJUSTMENT` line is not yet included in a payout run (`StatementConfirmed` for an agent with only corrections — E1 acceptance review Q1) is Phase 4.

---

## Appendix A. Scenario walkthrough (golden stream draft)

**A-1. Incentive over the limit, and surrender** — contracted 2026-08-01, monthly premium 150,000, incentive 1,000,000, closed and paid in 2026-09, surrendered 2027-02-15 (installment 7). Agent `A-1001`

> (v1.5.1) The canonical source is the **second edition** from engine E2 ⓪, `contracts/events/v1/samples/a1/a1_portal_150000_e2.jsonl` (engine `b6b65df`, SHA-256 `e9a03d37…eb751a`, 11 events — the first 4,517 bytes are the first edition below unchanged, with one added event, seq 11). (v1.4.2) The first edition is **the stream produced by engine E2 ⓪a**, `contracts/events/v1/samples/a1/a1_portal_150000.jsonl` (byte-identical to engine `ga-commission-engine@a124621` `commission-outbox/src/test/resources/outbox-golden/`, SHA-256 `25929e7f…abe718`, 10 events — Phase 2 approval Q13). Engine seed: FY rate 7.0 × payout rate 0.9 = monthly premium × 6.3, limit = monthly premium × 12, excess policy `DEFER_AFTER_FY` (the current engine behaves the same as a cut — E1-1), withholding tax 3.3%, 40% clawback for installments 7–12. Timestamps follow the engine's step clock (E2 Q9 — calculation 2026-08-01 09:00, close 09-05 18:00, payout 09-10 10:00, surrender 2027-02-15 09:30, +09:00). The previous example value (monthly premium of 300,000 won) could not be reproduced with the engine seed (DD-7). Forms the engine does not emit (a `LIMIT_DEFER` schedule and its cancellation, `StatementEstimated` — which comes in the E2 second edition) are contract examples under `samples/contract-only/`.

| seq | Event | Screen change |
|---|---|---|
| 1 | `LimitLedgerOpened`(limit 1,800,000) | Limit card created, 0% |
| 2 | `CommissionCalculated`(FY_COMM 945,000, 202608) | August estimated gross 945,000 |
| 3 | `CommissionCalculated`(INCENTIVE 855,000, limitCutAmt 145,000, policy DEFER_AFTER_FY) | gross 1,800,000, line shows "145,000 due to the limit", notification `LIMIT_CUT` |
| 4 | `LimitLedgerPosted`(945,000, accumAfter 945,000) | 52.5% |
| 5 | `LimitLedgerPosted`(855,000, accumAfter 1,800,000) | 100% FULL, notification `LIMIT_FULL` (the WARN and CRIT boundaries are crossed at the same time — only the highest one) |
| 6 | `StatementConfirmed`(202608, gross 1,800,000) | Status Confirmed (no after-tax amount — the close event does not carry the tax amount), notification |
| 7 | `PayoutCompleted`(202608, withholding tax 59,400, net = payout 1,740,600) | Paid |
| 8 | `PolicyEventObserved`(CANCEL, 2027-02-15) | Policy badge "Surrendered", notification |
| 9 | `CommissionCalculated`(CLAWBACK −720,000, 202702) | February offset shown, notification |
| 10 | `LimitLedgerPosted`(−720,000, accumAfter 1,080,000) | Utilization 60%, clawback deduction in history |
| 11 | `StatementEstimated`(202702, gross 0, clawbackOffset −720,000, tax 0 and net 0, basisSeq 10 — v1.5.1 second edition) | February "Estimated" — net 0, receivable carried over 720,000 (engine value `carriedReceivable` — not put into the payout run columns; screen wording is Phase 4) |

The two lines of one calculation arrive as `calc·calc·post·post` (the engine's post-save hook posts after all line saves are done) — the portal links them by `calcId` and does not rely on order (Phase 2 directive D-6).

**A-2. Deferral-scheme policy** — contracted 2027-02-01 (4-year curve example): `CommissionCalculated` (first-year payout portion) + `DeferralScheduled`×N (reason CURVE) → 48 months scheduled on the timeline; the monthly due-date batch's `DeferralStatusChanged(RELEASED)` → that month's `CommissionCalculated(DEFERRED)` → reflected in the statement. `HELD` in months where the policy is not maintained.

**A-3. Rebase on reduction** — `LimitLedgerRebased`(newLimit < accum) → "Rebased" badge on the limit card, remaining shown as 0; the event handling the excess follows the engine's policy parameters (the portal just follows the events).

(All amounts, rates and tax amounts are example values.)

## Appendix B. Demo tenant data specification (`portal-demo`)

- 1 tenant ("데모GA", "Demo GA"), 30 agents (1 headquarters · 2 branches · 6 teams), about 400 policies, contract dates evenly distributed from 2025-07 to 2027-06 → covering both sides of the 2026-06-30/07-01 boundary (limit not applied/applied) and the 2027-01-01 boundary (deferral not applied/applied).
- Monthly event generator: installment premium receipts, incentives, surrenders (5% per year, example), reductions (2% per year, example), close, payout.
- Cases that must be included: at least 1 each of limit 80/95/100%, 1 each of cut (CUT) and deferral (DEFER_AFTER_FY), 1 rebase, 1 HELD deferral, 1 clawback receivable carry-over, 1 reversal/re-entry pair, 1 post-close adjustment (ADJUSTMENT).
- The generated output is saved as an **event stream file (JSONL)** so that the portal can start without the engine, and in demo mode a file feed adapter replaces `/internal/v1/events`. Simulations return fixed stub responses in demo mode.

---

## Appendix C. Notes from a portfolio perspective

What to emphasize in the README: ① why it focuses on "showing" rather than recalculating money, and where that boundary lies (only sums and ratios) ② the three states Estimated/Confirmed/Paid and DRIFT "Verifying" — a case of turning trust into a UI convention ③ a read model that can be rebuilt without a broker, via the outbox pull feed ④ three-layer multitenant isolation (context, architecture tests, RLS) ⑤ contract version management between two repositories. As in the engine repository, record the AI collaboration methodology (design document → Phase directive → evidence-based acceptance) in one section.

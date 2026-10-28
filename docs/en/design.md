> 🌐 [한국어](../설계서.md) · **English** — translation of `docs/설계서.md` at commit `c3ac609`. The Korean original is canonical; where the two differ, the Korean text prevails.

# GA Agent "My Commissions & Limits" Dashboard Design Document (v1.11)

> Change history — v1.11 (2026-10-28, portal Phase 8 demo tenant and integrated run — directive `docs/phase-08-지시문.md`, plan `docs/phase-08-계획.md`, approval `docs/phase-08-계획승인.md`): §6.7 "what is restored" — the end-of-rebuild invariant (F11, `ViewReset.restoreDriftMarks`). Appendix B — the demo stream is made by the engine (copy of engine `9963809`, contract dates from 2026-03, window 2026-03 to 2028-02, limit rule period split and injected retention decisions — DD-21). §6.7 · §14.1 #11 setting key name `portal.ingest.rebuild-max-events` (P-18). `portal-demo` — the engine stand-in server (JDK HTTP, serves the three paths of the engine internal contract only from the demo bundle's bytes, 500 outside the samples, replay speed `instant` · `paced:<n>:<ms>` — addition only; the portal's `HttpFeedClient` passes the feed contract suite against it). §9 browser login — Authorization Code + PKCE (S256), popup + `BroadcastChannel` (condition B), refresh rotation, all in memory (`web/src/auth/oidc.ts`), unauthenticated `GET /auth/config` (contract 1.3.0 — exact match on the request origin, condition A), V10 `tenant.web_origin` · `oidc_client_id`, the registered issuer origin in the screen CSP `connect-src` (per request), the `/auth/callback` screen path. §7 contract 1.3.0. §14.1 #14 **closed**. §3.2 compose demo configuration (Keycloak, socat relay, Jaeger, secret generation, registration SQL) and management port, §11 stack (OTLP export, Prometheus scrape endpoint, image build). §12 all Phases marked complete (the Phase 9 line → §14.2). §13 rows for performance (k6 — example values call p95 ≤ 300 ms, bundle p95 ≤ 800 ms, P-2), demo and integration (compose E2E). §14 reorganized — §14.1 only the open items (#2 · #3 · #5 · #6 · #8 · #11), §14.2 one table of what remains for v2 (plan Q10), §14.3 the original text of items closed or moved (numbers kept).
>
> v1.10.1 (2026-10-28, **portal contract v1.4.1** — portal Phase 8 ⓪a, plan approval `docs/phase-08-계획승인.md`, DD-19): notation changes only. The description of `StatementEstimated.gross` — a provisional statement may add a preview of the deferral installments due that month, so there is no guarantee that it equals the portal Σ, and `basisSeq` is a lower bound (one sentence of the definition of aggregate amounts in §4.2). The constraints of the 15 event schemas are the same as 1.4.0 (`EventSchemaTest.descriptionOnlyChangesSince140` — digest of the tree with descriptions removed). The engine's copy is updated by a PR on the engine side.
>
> v1.10 (2026-10-27, portal Phase 7 reconciliation, automatic DRIFT recovery and observability — directive `docs/phase-07-지시문.md`, plan `docs/phase-07-계획.md`, approval `docs/phase-07-계획승인.md`): §3.4 flow 3, reconciliation, settled (module `portal-recon` — the verdict is a pure function; the CLI `recon run` and the in-app scheduler share one entry point). §5 write rules for `recon_result` (only `DRIFT` · `REPAIRED` — `MATCH` · `INCONCLUSIVE` write zero rows, only the engine fields compared, `engine_net` always NULL), V8 `recon_run` (zero value columns), V9 (fixes V8's `CHECK` letting NULL through — committed V files are not edited). §6.5 table gets the re-reconciliation `REPAIRED` row (Phase 6 acceptance Q1 — same key). §6.6 three verdicts (`MATCH` · `DRIFT` · `INCONCLUSIVE(사유)` (사유 = reason)), no DRIFT for ESTIMATE months (**Q1 ⓑ′**, **DD-19**), the lagging check (Q2), "new DRIFT" and "attempted" (Q14 — a DRIFT that has been attempted is not rebuilt again), if re-reconciliation is not `MATCH`, `data_status` is written back to DRIFT (**DE-28** — the rebuild erases it), an inconclusive re-reconciliation writes zero rows (approval condition A). §6.7 actor of the automatic rebuild `recon:<tenant>`. §10 metrics table → `docs/지표.md` (closed table — `MetricsCatalogTest`), tracing (span port `Tracing` — zero values), operational alert channel v1 = metrics + `status` CLI. §7 caching — the ETag `cfg` gets data marks (cursor update time, last reconciliation round) (**P7-15** — found by E2E ⓑ: reconciliation and rebuild change the body without changing `seq`, so a 304 revived an old body), statement count +1 for calls that use the sync context (§10). §12 Phase 7, §13 tests, §14.1 #11 (why the automatic rebuild runs at night) · #12 (gaps are filled only within the scope of month notifications — closed).
>
> v1.9 (2026-10-24, portal Phase 6 notification derivation and inquiries — directive `docs/phase-06-지시문.md`, plan `docs/phase-06-계획.md`, approval `docs/phase-06-계획승인.md`): contract `portal-api` 1.2.0 (§7 — five paths added, existing paths unchanged, `PortalApiCompatibilityTest`), V7 (§5 — `notification`'s `title` · `body` NULL only, closed shape of `params`, `link` format, `inquiry` category, length and answer columns, 24 closed values), module `portal-inbox` (§3.3 · §9 rule ⓖ), decision rules moved to `portal-domain` `rule` (`LimitStage` · `PolicyMask` · `RateLimitRule` — shared by queries, notifications, simulations and inquiries). §6.5 table replaced by the final table of plan ③ (**DD-18** — key = event seq, stage ↔ severity, no confirmation or payout notifications for DRIFT months, recipient = engine `agentId`), the derivation point is **inside the apply transaction, after the projector** (**RM-4** — reverts the separate deriver with its own cursor of Phase 5 acceptance §4 Q1), derivation exceptions are `APPLY_ERROR`. §6.7 a rebuild does not derive (settled — zero ArchUnit transitive dependencies; there are no notifications for facts a rebuild applies for the first time — Q25). §6.1 operator CLI `inquiry list|answer|close` (answers only via a file, control and format characters in the output as visible escapes). §8 home recent notifications and unread count, notification inbox, inquiry form, history and detail, entry points from line and monthly statements (approval A — the screens check `link` against the four paths), §9 CSRF rationale (three write paths), inquiry body isolation, 11 rejection bodies, §10 inquiry limit, notification retention, fifth home call, §12 Phase 6, §13 tests, §14.1 #3 (digest — v2) · #12 (replay derivation — closed) · #13 (V8 candidate).
>
> v1.8.1 (2026-10-24, portal Phase 5 acceptance review `docs/phase-05-수용심사.md` §2 B1): contract `portal-api` 1.1.1 (descriptions only) — §6.4 · §7 · §8 the timeline `months[]` holds only **months with expected or held amounts** (a month whose expected and held are both 0 — a month with only cancellations — has no row, like a month with no items; its cancelled items remain in `items[]`).
>
> v1.8 (2026-10-23, portal Phase 5 deferral timeline and simulation — directive `docs/phase-05-지시문.md`, plan `docs/phase-05-계획.md` (approved 2026-10-23, all recommendations)): contract `portal-api` 1.1.0 (§7 — additions and relaxations only, `PortalApiCompatibilityTest`), copy of the engine simulation samples (`contracts/shared/ga-commission-engine/` — engine `2c3b093`), access conventions moved to `com.ga.portal.domain.access` in `portal-domain` (§12 rule ⓒ — plan Q11). §6.4 scenarios, bars, 84 months, policy filter and multiplication isolation (an allowed set of three; amount multiplication only in `ForecastScenario` — plan Q16), §7 CR-1–4 applied, §10 fourth home call.
>
> v1.7.1 (2026-10-22, portal Phase 4 acceptance review `docs/phase-04-수용심사.md` §2 A1 · §3 Q9): §6.2 the label of correction amounts is not the month status but "지급 반영 미정" (payout inclusion undecided) (engine E1-2 — corrections are not read by close or payout runs, **RM-3**), §8 the same line, §14.1 #12 scope corrected, #14 the reload of "다시 시도" (try again) must be intercepted by the host.
>
> v1.7 (2026-10-17, portal Phase 4 screens, first round — directive `docs/phase-04-지시문.md`, plan `docs/phase-04-계획.md`, approval `docs/phase-04-계획승인.md`): §3.2 · §3.3 `web/` (same origin, PWA in v2 — P-6), §6.2 screen label table (16 cells — Phase 3 acceptance review Q1 · Q2), §6.3 `DEFER_AFTER_FY` wording (Q4), §6.6 `REBUILDING` also "확인 중" (under review) (P-3), §7 contract requests CR-1–4 (no API change), §8 screen conventions (paths, this month, chip, collapsing, limit card, correction wording), §9 token injection (fragment + memory — Q1) and screen response headers (Q2), §10 home call `status=ALL` (P-4), bundle limit 127 KiB (Q3), §11 screen stack, §12 Phase 4 scope (notification inbox in Phase 6 — P-5), §13 front-end tests, §14.1 #14 webview token renewal convention.
>
> v1.6.1 (2026-10-17, portal Phase 3 acceptance review `docs/phase-03-수용심사.md` §2 · §3): §7 the line list is 404 if there is no month row for that month (F5). §9 footnote to verification step ⑤ (`exp ≤ iat` → metric reason `SIGNATURE` — F9), JWKS failure metric `api_jwks_failures_total{issuer_hash}` (F6 — the only exception among metric tags), §10 the same metric. §14.1 turned into a table with an **implementation location** column (Q11 — the default assumptions of Phases 0–2 are not retrofitted with code markers but recorded in this column), #12 wording for unpaid corrections (Q3), #13 expected carried-over receivables (Q5).
>
> v1.6 (2026-10-17, portal Phase 3 query API, authentication and authorization — directive `docs/phase-03-지시문.md`, plan `docs/phase-03-계획.md`, approval `docs/phase-03-계획승인.md`): §5 V5 (`tenant.oidc_issuer`·`oidc_audience`, `identity_link.role`·`status`·`org_path`·`linked_at`, `agent_id` NULL only for `OPERATOR`, `ingest_cursor.caught_up_at`, `audit_access.detail`, `policy_limit.limit_multiple` → `TEXT` — `trim_scale(…)::text`, **DE-18**), `tenant.params.sync_delay_seconds` (step 2). §9 tenant = token `iss` (dedicated role `portal_issuer` — **DE-12**), verification order with eight reasons and byte-identical 401, `Caller` only from `identity_link`, byte-identical 404 and `AUTHZ_DENIED` audit (separate transaction; 401 only as a metric — **DE-17**), two `org_path` sources (**DE-13**), first entry of the direct-access allowlist (step 3), query API rules, second SQL predicate, canonical authorization matrix (step 5). §7 endpoints, two status fields `dataStatus` (sync — based on `caught_up_at`, **DE-14**) and `monthDataStatus` (reconciliation) (**DD-12**), own name as is (**DD-13**), no policy number — `policyMasked`·`policyKey` HMAC (**DE-15**), 400·401·404, weak ETag `seq.sync.cfg` (**DE-16**), contract `contracts/api/v1/portal-api.openapi.yaml` (step 4). §6.1 operator command authorization (`OPERATOR` link, `--actor` retired — step 6). §10 query metrics, statement count for the three home calls. §13 authorization and isolation rows. §14.1 #2·#5·#6·#9 (step 8).
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
[ga-commission-engine  (테넌트별 인스턴스, Oracle)]
   │  OUTBOX_EVENT (계산·원장·분급·환수·마감·지급 훅에서 같은 트랜잭션으로 적재)
   │
   ├─ GET  /internal/v1/events?afterSeq=…            ──┐ (pull, 커서)
   ├─ POST /internal/v1/simulations/new-contract     ──┤ (동기)
   ├─ POST /internal/v1/simulations/policy-event     ──┤
   └─ GET  /internal/v1/agents/{id}/statements/{ym}  ──┘ (대사·잠정 명세)
                                                       │
[ga-agent-portal (멀티테넌트, PostgreSQL)]             │
   portal-ingest   ─ 테넌트별 poller → 멱등 적용 → 프로젝터 → 알림 파생
   portal-query    ─ /api/v1/me/**  (설계사 self 조회, agent_id는 토큰에서만)
   portal-sim      ─ 엔진 동기 호출 프록시 + simulation_log
   portal-recon    ─ 일일 대사, DRIFT 처리, 재구축 명령
   web/            ─ React 모바일 웹, 같은 출처(portal-app이 서빙) — v1.7: 홈·명세·한도(분급·시뮬은 Phase 5, 알림은 Phase 6)
   ▲
   │ OIDC (테넌트별 IdP / 개발용 Keycloak)
[설계사 스마트폰·태블릿 / GA 기존 앱 웹뷰]
```

English rendering of the diagram above (labels translated; the block above is the Korean original, kept byte-identical):

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
   web/            ─ React mobile web, same origin (served by portal-app) — v1.7: home, statements, limits (deferrals and simulation in Phase 5, notifications in Phase 6)
   ▲
   │ OIDC (per-tenant IdP / Keycloak for development)
[agent smartphone/tablet / webview in the GA's existing app]
```

**Demo configuration** (v1.11 — portal Phase 8 ②–④, `docker/compose.yaml`, the single command `docker compose -f docker/compose.yaml --profile demo up --build` — every service is in the profile `demo` (directive C5)). Instead of the engine, `portal-demo` (serves the bytes of the engine's demo bundle over the three paths of the engine internal contract — appendix B); everything else is the portal image with the production settings as they are, with only the compose values different (zero demo-only product paths).

| Service | Image (all digest-pinned) | What it does | Exposed on host |
|---|---|---|---|
| `secrets` | busybox 1.38.0 | On first start, creates the secret files in the volume `secrets` (0600, owned by the consumer's uid — postgres 999, portal/demo 10001, Keycloak 1000): four DB roles + the superuser + `pgpass`, the engine service token (the same value for portal and demo), the policyKey HMAC key, the Keycloak administrator. Does not print the values. Unchanged when started again | — |
| `postgres` | PostgreSQL 18.6 (the same digest as the test harness) | Roles exactly as in `init-roles.sql`; passwords are passed by `00-roles.sql` from files into psql variables (zero arguments or environment) | — |
| `keycloak` | Keycloak 26.8.1 `start-dev --import-realm` | Realm `demo` (`docker/keycloak/realm.json` — output of the generator script; 30 agents, passwords as PBKDF2 hashes only; `portal-web` PKCE S256, `k6` Direct Grant, audience mapper `portal-demo`, access 300 seconds, refresh rotation). `KC_HOSTNAME=http://localhost:8180` | 127.0.0.1:8180 |
| `demo` | `demo` stage of the portal image build | `portal-demo` (`--replay=instant`, token from a file) | — |
| `portal` | `portal` stage of the portal image build (source bundle of the public `ga-disclosure` tag `platform-v0.2.0` → `publishToMavenLocal` → `bootJar` — no credentials needed; the verification metadata enforces the same bytes as the published artifacts) | Application port 8080, management port 9090 (`/actuator/prometheus` and health — a security chain dedicated to the management port; the same paths on the application port give the same 404), DB passwords via Spring configtree (files named like the placeholders), ingest is switched on only by a server argument (so that it is not carried into CLIs invoked with `exec`), OTLP → Jaeger, 100% sampling | 127.0.0.1:8080 |
| `idp-relay` | socat 1.8.1.3, sharing the portal's network namespace | `localhost:8180` inside the portal container → `keycloak:8080` — the browser and the portal use the same issuer string (plan P-14) | — |
| `jaeger` | Jaeger 2.22.0 (OTLP receiver, in-memory storage) | Trace UI | 127.0.0.1:16686 |
| `seed` | psql of the PostgreSQL image | After the portal is up, `seed-demo.sql` (the SQL of docs/운영.md §1–§3 as is — tenant `DEMO` "데모GA" (Demo GA), 30 agents, operator `cli:portal`, polling 5 seconds + 1,000 events per ingest page → first sync about 2–3 minutes — 138–149 s measured in CI) | — |

### 3.3 Module composition (Gradle multi-module + pnpm)

```
ga-agent-portal/
├── contracts/                # 엔진↔포털 계약: JSON Schema (events/v1/*.json, api/v1/openapi.yaml) + shared/ga-disclosure/(공유 Envelope 핵심 사본, v1.2)
├── portal-domain             # 포털 고유 값객체(CalcId, PolicyNo, LedgerId, ScheduleId, CommType), 뷰 모델, 상태 열거형. platform-core만 의존
├── portal-projection         # 프로젝터(이벤트→뷰 변환) 순수 로직 + 알림 파생 규칙. DB 무의존
├── portal-ingest             # 이벤트 피드 클라이언트, 커서, 멱등 적용, 실패 격리, 재구축 실행기
├── portal-query              # 조회 서비스·REST 컨트롤러, 인가 정책
├── portal-sim                # 엔진 시뮬레이션·명세 dry-run 프록시, simulation_log
├── portal-inbox              # (v1.9) 알림함·이의제기 유스케이스(목록·읽음·문의 접수·조회)와 계약 레코드. Spring·DB 무의존 — 저장은 포트(app 어댑터)
├── portal-recon              # (v1.10) 대사 판정(순수 함수)·회차·자동 복구 순서와 엔진 명세 클라이언트. Spring·DB 무의존 — 저장·잠금·재구축·지표는 포트(app 어댑터)
├── portal-infra              # PostgreSQL 어댑터(platform-spring `TenantScopedRepository` 하위 저장소), RLS 세션 바인딩, Flyway
├── portal-app                # Spring Boot 조립, 보안 설정, 스케줄러
├── portal-demo               # 데모 테넌트 데이터 생성기(§부록 B), 로컬 엔진 대체용 이벤트 재생기
└── web/                      # React 19 + TypeScript + Vite, pnpm — 빌드 산출물은 portal-app 부트 jar 안(같은 출처, v1.7). PWA(서비스 워커)는 v2
```

English rendering of the module tree above (comments translated; the block above is the Korean original, kept byte-identical):

```
ga-agent-portal/
├── contracts/                # engine↔portal contract: JSON Schema (events/v1/*.json, api/v1/openapi.yaml) + shared/ga-disclosure/ (copy of the shared Envelope core, v1.2)
├── portal-domain             # portal-specific value objects (CalcId, PolicyNo, LedgerId, ScheduleId, CommType), view models, status enums. Depends only on platform-core
├── portal-projection         # projectors (event→view transformation) pure logic + notification derivation rules. DB-independent
├── portal-ingest             # event feed client, cursor, idempotent apply, failure isolation, rebuild executor
├── portal-query              # query services, REST controllers, authorization policy
├── portal-sim                # engine simulation and statement dry-run proxy, simulation_log
├── portal-inbox              # (v1.9) notification inbox and inquiry use cases (list, mark read, submit inquiry, view) and contract records. Independent of Spring and DB — storage via ports (app adapters)
├── portal-recon              # (v1.10) reconciliation verdict (pure function), round and automatic recovery sequence, and the engine statement client. Independent of Spring and DB — storage, locks, rebuild and metrics via ports (app adapters)
├── portal-infra              # PostgreSQL adapters (repositories under platform-spring `TenantScopedRepository`), RLS session binding, Flyway
├── portal-app                # Spring Boot assembly, security configuration, schedulers
├── portal-demo               # demo tenant data generator (§Appendix B), event replayer standing in for a local engine
└── web/                      # React 19 + TypeScript + Vite, pnpm — build output inside the portal-app boot jar (same origin, v1.7). PWA (service worker) in v2
```

Dependency direction: `app → query/ingest/sim/inbox/recon → projection → domain` (v1.9 `portal-inbox` — the notification derivation rules are in `projection`, derivation runs in the ingest apply transaction, the operator CLI is in `app`). `infra` is assembled as an adapter only in `app`. `projection` must be independent of DB and Spring (millisecond-level tests with in-memory repositories), the same principle as the engine's `commission-calc`.

**Platform artifact (v1.2)**: the shared value objects `Won`·`Ratio`·`Ym`·`TenantId`·`AgentId` and `TenantContext` come from `com.ga.platform:platform-core` 0.2.0 published by ga-disclosure (§11 platform row). The portal does not create classes with the same simple names (architecture test), and if the platform API falls short, the portal does not supplement it but escalates it as maintenance work on the ga-disclosure side. The only external module `portal-domain` and `portal-projection` may depend on is `platform-core`; `platform-spring` (session binding, repository base classes) is used only in `infra` and `app`.

### 3.4 Three data flows

1. **Projection path (asynchronous)**: engine transaction commit → OUTBOX load → portal poller (per tenant, every 5–30 seconds) → `ingest_event` idempotency check → projector apply + notification derivation (same transaction) → cursor advance. Screens always display `asOf(seq, at)` alongside.
2. **Synchronous path**: simulation or provisional statement refresh request → `portal-sim` → engine API (service token) → result log → response. When the engine is unavailable, the screen shows "try again shortly" + the time of the last snapshot, and **projection-path data continues to be served as is**.
3. **Reconciliation path (batch)** (settled in v1.10 — §6.6): once a day per tenant (the first scheduler tick after KST `recon_hour_kst`, or the operator CLI `recon run`), for each `earnings_month` row of the target months the engine statement (`GET /internal/v1/agents/{agentId}/statements/{ym}?mode=` — zero retries, concurrency 1, limits) is fetched and three integers are compared for equality → `MATCH` (zero rows) · `DRIFT` (that month's `data_status = DRIFT` + one `recon_result` row, the screen shows "확인 중" (under review)) · `INCONCLUSIVE(사유)` (zero rows, metrics only) → if there is a new DRIFT or a DRIFT not yet attempted, automatic rebuild is on and there has been no rebuild today, rebuild (§6.7, actor `recon:<tenant>`) → re-reconcile only the unresolved months (`REPAIRED` if `MATCH`) → whatever remains goes to metrics and `status` (operational alert channel v1).

---

## 4. Engine↔Portal Integration Contract (Integration Contract v1)

The contract lives in the `contracts/` folder as JSON Schema and OpenAPI. The engine repository has **provider tests that validate published events** against the same schemas (§13). Schema changes follow incrementing the `version` field and the compatibility rules (§4.5).

**Contract conventions (v1.2)**:
- `contracts/` has two parts. `contracts/shared/ga-disclosure/` is a copy of the **shared Envelope core** (`events/v1/envelope-core.schema.json`) owned by ga-disclosure and is not edited — if it needs to change, a small PR in ga-disclosure → merge → move `UPSTREAM` to that merge commit and fetch it again. Everything else (`events/v1`·`api/v1`) is portal canonical.
- The copy directory has its own `CHECKSUMS` (plain SHA-256 of file bytes, `sha256sum -c` format) and `UPSTREAM` (first line `hjryoo-ai/ga-disclosure@<40자 병합 커밋>` (a 40-character merge commit), file list from the next line on). `verifyContractChecksums` (offline, `check`) checks the portal-canonical `contracts/CHECKSUMS` and the copy's `CHECKSUMS`; `verifyUpstreamContract` (network, CI) byte-compares **only the copy** with upstream — comparing all of `contracts/` would fail because the portal-canonical files do not exist upstream. JCS (`platform-canonical`) is used only for the canonical hash of event payloads (idempotency and duplicate detection).
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
GET /internal/v1/events?afterSeq={n}&limit={1~1000, 기본 200}
→ { "tenantId": "GA01", "events": [Envelope...], "nextSeq": n', "headSeq": h, "schemaVersion": 1 }
```

(`기본 200` = default 200.)

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

The portal Envelope schema (`contracts/events/v1/envelope.schema.json`, v1.2) is `allOf: [공유 핵심, 포털 제약]` (shared core, portal constraints). The core is seven fields (`eventId` UUID, `seq` ≥ 1, `type`, `version` ≥ 1, `occurredAt` including offset, `aggregate{kind, id}`, `payload` object) and their formats; the portal constraints are 13 `type` values (§4.2), `version` 1, 9 `aggregate.kind` values and an **`if/then` per `type` (+`version`)** (payload schema, `type` ↔ `kind` pairing), and any `type` or `version` outside the enumeration is rejected (else: false). Payloads are not selected with `oneOf` — that is incompatible with open payloads that allow unknown fields (a payload whose fields overlap would match both and fail, and a payload unrelated to its `type` would also pass).

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

**Definition of aggregate amounts** (v1.3 — the same sentence in the contract description and the §5 comments): `gross` = Σ amount of that month's `CommissionCalculated` lines with commType ∉ {CLAWBACK, ADJUSTMENT} — **summed across all statuses** (both cancelled originals and reversals; not filtered by status), `clawbackOffset` = Σ that month's CLAWBACK lines (≤ 0), `adjustment` = Σ `AdjustmentPosted` attributed to that month. The three fields of `StatementEstimated`, `StatementConfirmed` and `PayoutCompleted` follow this definition, and they must equal the sums in the portal's `earnings_month` for the §6.6 reconciliation to hold. However (v1.10.1 — contract v1.4.1, DD-19), `StatementEstimated.gross` may add a preview of the deferral installments due that month — `gross` is not reconciled for provisional months (§6.6 Phase 7 Q1 ⓑ′), and `basisSeq` is a lower bound. `PayoutCompleted.receivableOffset` (automatic offset of existing receivables) is different from `clawbackOffset`.

Clawback is not a separate type. In the engine a clawback is a negative `COMM_CALC`, so it arrives as `CommissionCalculated(commType=CLAWBACK, amount<0)`, and only whether it became a receivable is supplemented by `ClawbackReceivableUpdated`.

### 4.3 Synchronous API (provided by the engine)

| API | Request | Response | Purpose |
|---|---|---|---|
| `POST /internal/v1/simulations/new-contract` | agentId, productKey, monthlyPremium, contractDate, paymentTermMonths?, insurerCd? (v1.4.2 — the rate key is (insurer, product). When absent, if the number of candidate insurers is not exactly 1 (0 or 2+), 422 `INSURER_AMBIGUOUS` — v1.5.1), incentiveCodes[]? (engine incentive codes — if given, only those incentives) | limitAmount?·projectedAccum? (v1.4.2 — absent means a policy with no limit applied), fyCommissionByInstallment[{'no', amount}] (one per installment — key in quotes, v1.5.1), incentives[{code, amount (after the cut — same definition as `CommissionCalculated.amount`), limitCutAmt?, limitIncluded}] (summed per code), deferral{firstYearPayout (= Σ installment commissions + Σ incentives), schedule[{dueYm, amount}] (summed per dueYm)}, ruleSnapshotId, generatedAt · 400 · 404 `AGENT_NOT_FOUND` · 422 | Q5 |
| `POST /internal/v1/simulations/policy-event` | agentId, policyNo, eventType, asOfDate, elapsedInstallments? (elapsed installment count — if absent, approximated by number of months), newMonthlyPremium? (recalculation input for REDUCE) | clawbackAmount (sign is the same as `CommissionCalculated` — surrender and lapse ≤ 0, reinstatement repayment > 0, v1.5.1), offsetAgainstMonth{closeYm, before, after} (before and after of that month's pre-tax net before receivable offset = gross + clawbackOffset), receivableRemaining (sum of open receivable balances after this event = open receivable balance + max(0, −after), v1.5.1), cancelledDeferrals[{scheduleId, dueYm, amount}], ruleSnapshotId · 400 · 404 `AGENT_NOT_FOUND`·`POLICY_NOT_FOUND` · 422 `UNSUPPORTED_EVENT_TYPE` (PAYMENT_OVERDUE) | Q4 |
| `GET /internal/v1/agents/{agentId}/statements/{ym}?mode=ESTIMATE\|CONFIRMED` | — | ESTIMATE = the provisional statement computed now (in the shape of StatementEstimated, not published), CONFIRMED = the shape of StatementConfirmed + withholdingTax·net after payout (sum of that month's payout runs) — the same values as that month's events (v1.4.2) · 400 · 404 `AGENT_NOT_FOUND`·`STATEMENT_NOT_CONFIRMED` · 422 `MONTH_CLOSED` (ESTIMATE for a closed month) | Reconciliation, on-demand refresh (rate-limited) |
| `GET /internal/v1/events` | afterSeq, limit? (1–1000, default 200) | tenantId, events, nextSeq, headSeq, schemaVersion · 400 `INVALID_REQUEST` · 401 · 422 `AFTER_BEYOND_HEAD` (§4.1) | Projection |

**Simulation on the portal side** (v1.8 — portal Phase 5, `portal-sim`): **only the server** calls the engine (the screens do not know the engine), zero retries, connect 2 seconds and read 10 seconds (application setting `portal.sim.*`), the service token is the same file as for ingest. The portal does not send incentive codes (the engine applies every incentive that fits that policy — plan Q4). An engine response is accepted only if it passes the contract copy's schema and its numbers are in integer notation, and **only the properties of the copy's schema are carried over, values as they are** (unknown fields are dropped and only their count is logged — the portal contract forbids additional properties). Engine answer → portal: 200 + passes the schema → 200 · 200 violating it, engine 400 · 404, or an unexpected status → 502 `ENGINE_CONTRACT` (not the agent's fault but a contract or operations problem) · connection failure, timeout, engine 5xx, 401/403 → 503 `ENGINE_UNAVAILABLE` (degraded mode; queries are not affected) · 422 `INSURER_AMBIGUOUS` · `UNSUPPORTED_EVENT_TYPE` keep that code, any other 422 is `ENGINE_REJECTED`. The engine `message` is not carried into any response. The data source is the copy of the engine samples (`contracts/shared/ga-commission-engine/samples/simulations/` — the files the engine's `SimulationContractTest` compares with the engine's responses; the only engine data for stubs and E2E).

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

**Constraints (v1.2)** — no columns were added; only constraints were added to V1. ① Format `CHECK` (regex): `tenant_id` = platform `TenantId` (`^[A-Z0-9][A-Z0-9_]{0,31}$`), `agent_id` = platform `AgentId` (`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`), `close_ym` and `due_ym` = `Ym` (`yyyyMM`), `event_id` and `last_event_id` = the UUID regex of the Envelope core. ② Closed-value `CHECK (… IN …)`: the columns annotated with a `-- A/B/C` comment in the DDL below — 12 in v1.2 + 7 in V4 (v1.5) = **19** (v1.9: + 2 in V5 · 1 in V6 · 2 in V7 = **24**). The value list is that comment (for the 7 without an enum) or a portal enum (`MonthStatus`, `DataStatus`, `DeferralStatus`, `IngestState`, `IngestEventStatus`, `AgentStatus`, `ReceivableStatus`, `NetBasis`, `ReconSource`, `FlagType`, v1.6 `Role` · `LinkStatus`, v1.8 `SimulationKind`, v1.9 `NotificationType` · `Severity` · `InquiryStatus` · `InquiryCategory`), and `StatusEnumDdlTest` cross-checks the three (comment, every V, enum). Open lists ending in `/...` (`comm_type`, `role`) have no `CHECK`. ③ The unique key of `notification` is scoped to the agent (Q1).

```sql
CREATE TABLE tenant (
  tenant_id        TEXT PRIMARY KEY,
  name             TEXT NOT NULL,
  engine_base_url  TEXT NOT NULL,                -- D-2: 테넌트별 엔진 인스턴스
  -- status의 뜻(v1.4.1·DD-6): 관리 상태(GA 계약 중지·온보딩 전). PAUSED = 폴링·조회 모두 중지 — 커서 상태(ingest_cursor.state = 운영 상태)와 독립
  status           TEXT NOT NULL,                -- ACTIVE/PAUSED
  limit_warn_pct   NUMERIC(5,2) NOT NULL DEFAULT 80,   -- 예시값. 포털은 SQL에서 정수 bp(round(x * 100)::bigint)로 읽어 Ratio로만 쓴다 — BigDecimal 금지(v1.2)
  limit_crit_pct   NUMERIC(5,2) NOT NULL DEFAULT 95,   -- 예시값. 같은 규약
  payout_day_rule  TEXT NOT NULL,                -- 예: "NEXT_MONTH_D10" (지급예정일 표시용)
  params           JSONB NOT NULL DEFAULT '{"org_member_lines_visible": false, "forecast_scenario_enabled": false, "notification_digest": false}'
                                                 -- 사규 파라미터. 확정된 키(§14.0) — 기본값에 세 키를 둔다(v1.2):
                                                 --   org_member_lines_visible : bool, 기본 false (조직장 뷰의 개별 팀원 라인 노출)
                                                 --   forecast_scenario_enabled: bool, 기본 false (분급 타임라인 유지율 가정 토글)
                                                 --   notification_digest      : bool, 기본 false
                                                 --   sync_delay_seconds       : int, 없으면 설정 portal.query.default-sync-delay-seconds(기본 300) — v1.6, §7 dataStatus
  oidc_issuer      TEXT UNIQUE,                  -- v1.6 V5: 이 테넌트 토큰의 iss(정확 일치). oidc_audience와 둘 다 NULL이면 로그인 불가 테넌트(§9)
  oidc_audience    TEXT                          -- v1.6 V5: 토큰 aud에 있어야 하는 값. 두 열은 전용 롤 portal_issuer만 테넌트 바인딩 전에 읽는다
);

CREATE TABLE identity_link (            -- IdP subject ↔ 설계사 매핑. agent_id·역할은 여기서만 결정된다(v1.6 — 토큰 클레임이 아니다)
  tenant_id TEXT, subject TEXT,
  agent_id  TEXT,                        -- v1.6 V5: NULL은 OPERATOR만(CHECK — 운영 CLI 주체 cli:<OS 사용자>에는 설계사가 없다)
  role      TEXT NOT NULL,               -- AGENT/MANAGER/OPERATOR
  status    TEXT NOT NULL,               -- ACTIVE/REVOKED
  org_path  TEXT,                        -- v1.6 인가용 조직(포털 사실, v2 조직장 범위) — OPERATOR 밖은 필수. agent_profile.org_path(엔진 사실·표시용)와 다른 출처
  linked_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (tenant_id, subject)
);

CREATE TABLE agent_profile (
  tenant_id TEXT, agent_id TEXT, name TEXT NOT NULL, grade TEXT, org_path TEXT NOT NULL, -- 엔진이 준 org_id 경로 그대로(예: /HQ1/BR03/TM07)
  role TEXT,                             -- AGENT/TEAM_LEAD/BRANCH_HEAD/... (v1.5 NULL 허용 — 엔진에 역할이 없다, 역할은 identity_link·Phase 3)
  status TEXT NOT NULL,                  -- ACTIVE/DISMISSED
  valid_from DATE NOT NULL, last_seq BIGINT NOT NULL,
  PRIMARY KEY (tenant_id, agent_id)
);

CREATE TABLE earnings_line (
  tenant_id TEXT, calc_id TEXT, agent_id TEXT NOT NULL, policy_no TEXT NOT NULL,
  product_key TEXT, comm_type TEXT NOT NULL,     -- FY_COMM/RENEWAL/INCENTIVE/OVERRIDE/DEFERRED/CLAWBACK/ADJUSTMENT/...
  amount BIGINT NOT NULL, close_ym TEXT NOT NULL,  -- amount = 한도 삭감 뒤 지급분(요청액 = amount + limit_cut_amt, v1.2 I1)
  reversal_of_calc_id TEXT, limit_cut_amt BIGINT NOT NULL DEFAULT 0, limit_cut_policy TEXT,
  basis JSONB NOT NULL, policy_summary JSONB NOT NULL, source_seq BIGINT NOT NULL,  -- basis의 rate·payoutRate는 불투명 소수 문자열(표시 전용, v1.2)
  PRIMARY KEY (tenant_id, calc_id)
);
CREATE INDEX ix_line_agent_ym ON earnings_line (tenant_id, agent_id, close_ym);
CREATE INDEX ix_line_policy   ON earnings_line (tenant_id, agent_id, policy_no);

CREATE TABLE earnings_month (
  tenant_id TEXT, agent_id TEXT, close_ym TEXT,
  gross BIGINT NOT NULL DEFAULT 0,         -- 포털 합산: Σ 그 달 CommissionCalculated 라인 중 comm_type ∉ {CLAWBACK, ADJUSTMENT} — 전 상태 합산(취소 원본·reversal 모두)
  clawback_offset BIGINT NOT NULL DEFAULT 0, -- 포털 합산: Σ 그 달 CLAWBACK 라인(≤ 0)
  adjustment BIGINT NOT NULL DEFAULT 0,    -- 포털 합산: Σ 그 달로 귀속된 AdjustmentPosted(comm_type ADJUSTMENT 라인)
  withholding_tax BIGINT,                  -- 엔진 명세 스냅샷에서만 채움 (NULL = 아직 없음)
  net BIGINT,                              -- 엔진 명세 스냅샷에서만 채움
  status TEXT NOT NULL,                    -- PROVISIONAL/ESTIMATED/CONFIRMED/PAID
  -- data_status: DRIFT = 마감·지급 이벤트의 합계가 포털 합산과 다르다(§6.6, 독 아님). REBUILDING은 미사용(v1.5 Q9 — 재구축 신호는 커서 상태)
  data_status TEXT NOT NULL DEFAULT 'OK',  -- OK/DRIFT/REBUILDING
  estimate_as_of TIMESTAMPTZ, estimate_basis_seq BIGINT,  -- StatementEstimated의 asOf·basisSeq(검사 없이 기록)
  confirmed_at TIMESTAMPTZ, paid_at TIMESTAMPTZ, paid_amount BIGINT,
  receivable_offset BIGINT, carried_receivable BIGINT, payable BIGINT, run_seq BIGINT,  -- v1.5: PayoutCompleted의 지급 런 확정값(엔진 값)
  net_basis TEXT,                          -- ESTIMATE/CONFIRM/PAYOUT
  last_seq BIGINT NOT NULL,
  PRIMARY KEY (tenant_id, agent_id, close_ym)
);

CREATE TABLE policy_limit (
  tenant_id TEXT, ledger_id TEXT, agent_id TEXT NOT NULL, policy_no TEXT NOT NULL,
  contract_date DATE NOT NULL, fy_start DATE NOT NULL, fy_end DATE NOT NULL,
  monthly_premium BIGINT NOT NULL, limit_multiple TEXT NOT NULL, limit_amount BIGINT NOT NULL,  -- limit_multiple은 표시 전용 불투명 소수 문자열(곱하지 않는다) — v1.6 V5부터 계약 decimalString 원문(TEXT)
  accum_paid BIGINT NOT NULL DEFAULT 0,    -- 엔진 accumAfter를 그대로 저장 (포털 합산 금지)
  cut_total BIGINT NOT NULL DEFAULT 0, deferred_total BIGINT NOT NULL DEFAULT 0,
  last_posting_seq BIGINT NOT NULL DEFAULT 0, rule_version_id TEXT NOT NULL,
  PRIMARY KEY (tenant_id, ledger_id),
  UNIQUE (tenant_id, policy_no, agent_id)
);
CREATE TABLE policy_limit_history (       -- 원장 타임라인 (전기·재산정)
  tenant_id TEXT, ledger_id TEXT, source_seq BIGINT NOT NULL,  -- v1.5: 키 = 그 사실의 이벤트 seq(DD-5 — REBASE는 전기 순번이 없을 수 있다)
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
  tenant_id TEXT, notification_id UUID, agent_id TEXT NOT NULL,
  type TEXT NOT NULL,                    -- LIMIT_WARN/LIMIT_CRIT/LIMIT_FULL/LIMIT_REBASE/LIMIT_CUT/CLAWBACK/DEFER_HELD/DEFER_CANCELLED/POLICY_PAYMENT_OVERDUE/POLICY_LAPSE/POLICY_CANCEL/STMT_CONFIRMED/PAYOUT/INQUIRY_ANSWERED
  severity TEXT NOT NULL,                -- INFO/WARN/CRIT
  dedup_key TEXT NOT NULL, title TEXT, body TEXT, link TEXT,   -- v1.9 V7: title·body는 NULL만(죽인 열 — 서버는 문장을 만들지 않는다), link는 화면 경로 넷의 형식
  params JSONB NOT NULL DEFAULT '{}',    -- v1.9 V7: type별 닫힌 모양(계약 1.2.0 Notification 변이) — 금액 없음, 문장은 화면 템플릿(§6.5)
  created_at TIMESTAMPTZ NOT NULL, read_at TIMESTAMPTZ,
  PRIMARY KEY (tenant_id, notification_id), UNIQUE (tenant_id, agent_id, dedup_key)  -- v1.2 Q1: dedup_key는 설계사 안에서 유일
);
-- v1.9 V7: 인덱스 (tenant_id, agent_id, created_at DESC).

CREATE TABLE inquiry (
  tenant_id TEXT, inquiry_id UUID, agent_id TEXT NOT NULL, calc_id TEXT, category TEXT NOT NULL,  -- AMOUNT/LIMIT/CLAWBACK/DEFERRAL/OTHER
  message TEXT NOT NULL, status TEXT NOT NULL,   -- OPEN/ANSWERED/CLOSED
  created_at TIMESTAMPTZ NOT NULL, answered_at TIMESTAMPTZ, answer TEXT, answered_by TEXT,
  PRIMARY KEY (tenant_id, inquiry_id)
);
-- v1.9 V7: CHECK — OTHER 밖은 calc_id 필수, message 1~1,000 코드 포인트(공백만 안 됨), answer 1~4,000, answered_by = cli:<OS 사용자>,
--          상태별 답변 열(OPEN 셋 다 NULL·ANSWERED 셋 다 있음·CLOSED 둘 중 하나). 인덱스 (tenant_id, agent_id, created_at DESC) — 목록·24시간 창.

CREATE TABLE simulation_log (
  tenant_id TEXT, sim_id UUID, agent_id TEXT NOT NULL,
  kind TEXT NOT NULL,                                          -- NEW_CONTRACT/POLICY_EVENT
  request JSONB NOT NULL, response JSONB, rule_snapshot_id TEXT, status TEXT NOT NULL,  -- REQUESTED/SUCCEEDED/FAILED
  created_at TIMESTAMPTZ NOT NULL,
  error_code TEXT, engine_status INTEGER,     -- v1.8 V6: 실패 사유(포털 사유 또는 엔진 코드, 형식 ^[A-Z][A-Z0-9_]*$)와 엔진 HTTP 상태
  PRIMARY KEY (tenant_id, sim_id)
);
-- v1.8 V6: 인덱스 (tenant_id, agent_id, created_at) — 레이트 리밋 창. CHECK: 상태별 열 — REQUESTED는 결과 열 전부 NULL,
--          SUCCEEDED는 response·rule_snapshot_id 있음·error_code 없음, FAILED는 error_code 있음·response·rule_snapshot_id 없음.

CREATE TABLE ingest_cursor (
  tenant_id TEXT PRIMARY KEY, last_seq BIGINT NOT NULL, last_event_id TEXT, state TEXT NOT NULL, -- RUNNING/PAUSED/FAILED/REBUILDING
  updated_at TIMESTAMPTZ NOT NULL, error TEXT,   -- updated_at = 마지막 적용·정지·운영 명령 시각(유휴 폴링은 바꾸지 않는다)
  caught_up_at TIMESTAMPTZ                       -- v1.6 V5: 회차가 피드를 받고 last_seq = headSeq·RUNNING으로 끝난 마지막 시각 — §7 SYNC_DELAYED의 기준
);
CREATE TABLE ingest_event (                -- 멱등키 + 적용 감사
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
  source_seq BIGINT NOT NULL,              -- v1.5: EVENT = 그 이벤트의 seq, BATCH = 0 (같은 시각의 확정·지급 DRIFT가 키에서 갈린다 — Q2)
  source TEXT NOT NULL,                    -- EVENT/BATCH
  PRIMARY KEY (tenant_id, agent_id, close_ym, run_at, source, source_seq)
);

CREATE TABLE recon_run (                   -- v1.10 V8: 대사 회차 기록(테넌트×회차 시작 시각 한 행). 값 열 0 — 수와 닫힌 값만(금액·설계사·달 없음)
  tenant_id TEXT, run_at TIMESTAMPTZ,      -- run_at = 회차 시작 = 그 회차 DRIFT 행의 run_at(Q5)
  actor TEXT NOT NULL,                     -- SCHEDULE이면 recon:<tenant>, CLI면 cli:<OS 사용자>
  trigger TEXT NOT NULL,                   -- SCHEDULE/CLI
  checked INT NOT NULL, matched INT NOT NULL, drift_new INT NOT NULL, drift_unresolved INT NOT NULL,
  inconclusive INT NOT NULL, engine_calls INT NOT NULL,
  result TEXT NOT NULL,                    -- OK/DRIFT/REPAIRED/FAILED
  rebuilt_at TIMESTAMPTZ,
  rebuild_outcome TEXT,                    -- SUCCEEDED/FAILED
  PRIMARY KEY (tenant_id, run_at)
);

CREATE TABLE audit_access (                -- 타인 데이터 조회·관리 행위 감사
  tenant_id TEXT, id BIGSERIAL, actor_subject TEXT, actor_agent_id TEXT, action TEXT, target TEXT,
  at TIMESTAMPTZ NOT NULL, PRIMARY KEY (tenant_id, id),
  detail TEXT                                -- v1.6 V5: 거부 사유 코드(AUTHZ_DENIED — 닫힌 값, 값을 담지 않는다)
);
```

English gloss of the Korean comments in the DDL above (the block is the Korean original, kept byte-identical; each item names the table and the line it annotates):

- `tenant`:
  - `engine_base_url  TEXT NOT NULL` — D-2: per-tenant engine instance
  - comment — meaning of status (v1.4.1, DD-6): administrative status (GA contract suspended, before onboarding). PAUSED = both polling and queries stop — independent of the cursor state (ingest_cursor.state = operational state)
  - `limit_warn_pct   NUMERIC(5,2) NOT NULL DEFAULT 80` — example value. The portal reads it in SQL as integer bp (round(x * 100)::bigint) and uses it only as a Ratio — no BigDecimal (v1.2)
  - `limit_crit_pct   NUMERIC(5,2) NOT NULL DEFAULT 95` — example value. Same convention
  - `payout_day_rule  TEXT NOT NULL` — e.g. "NEXT_MONTH_D10" (for displaying the scheduled payout date)
  - comment — company-policy parameters. Confirmed keys (§14.0) — the default holds three keys (v1.2):
  - comment — org_member_lines_visible : bool, default false (shows individual team members' lines in the org manager view)
  - comment — forecast_scenario_enabled: bool, default false (retention-rate assumption toggle on the deferral timeline)
  - comment — notification_digest      : bool, default false
  - comment — sync_delay_seconds       : int; if absent, the setting portal.query.default-sync-delay-seconds (default 300) — v1.6, §7 dataStatus
  - `oidc_issuer      TEXT UNIQUE` — v1.6 V5: iss of this tenant's tokens (exact match). If both this and oidc_audience are NULL, the tenant cannot log in (§9)
  - `oidc_audience    TEXT` — v1.6 V5: value that must be present in the token's aud. Only the dedicated role portal_issuer reads these two columns, before tenant binding
- `identity_link`:
  - table comment — IdP subject ↔ agent mapping. agent_id and role are determined only here (v1.6 — not by token claims)
  - `agent_id  TEXT` — v1.6 V5: NULL only for OPERATOR (CHECK — the operations CLI principal `cli:<OS 사용자>` (OS user) has no agent)
  - `org_path  TEXT` — v1.6 organization for authorization (portal fact, v2 org manager scope) — required except for OPERATOR. A different source from agent_profile.org_path (engine fact, for display)
- `agent_profile`:
  - `tenant_id TEXT, agent_id TEXT, name TEXT NOT NULL, grade TEXT, org_path TEXT NOT NULL` — org_id path exactly as given by the engine (e.g. /HQ1/BR03/TM07)
  - `role TEXT` — AGENT/TEAM_LEAD/BRANCH_HEAD/... (v1.5 NULL allowed — the engine has no role; roles come from identity_link, Phase 3)
- `earnings_line`:
  - `amount BIGINT NOT NULL, close_ym TEXT NOT NULL` — amount = payout after the limit cut (requested amount = amount + limit_cut_amt, v1.2 I1)
  - `basis JSONB NOT NULL, policy_summary JSONB NOT NULL, source_seq BIGINT NOT NULL` — rate and payoutRate in basis are opaque decimal strings (display only, v1.2)
- `earnings_month`:
  - `gross BIGINT NOT NULL DEFAULT 0` — portal sum: Σ of the month's CommissionCalculated lines with comm_type ∉ {CLAWBACK, ADJUSTMENT} — summed across all states (both reversed originals and reversals)
  - `clawback_offset BIGINT NOT NULL DEFAULT 0` — portal sum: Σ of the month's CLAWBACK lines (≤ 0)
  - `adjustment BIGINT NOT NULL DEFAULT 0` — portal sum: Σ of AdjustmentPosted attributed to the month (comm_type ADJUSTMENT lines)
  - `withholding_tax BIGINT` — filled only from engine statement snapshots (NULL = not yet available)
  - `net BIGINT` — filled only from engine statement snapshots
  - comment — data_status: DRIFT = the totals of a close/payout event differ from the portal sum (§6.6, not poison). REBUILDING is unused (v1.5 Q9 — the rebuild signal is the cursor state)
  - `estimate_as_of TIMESTAMPTZ, estimate_basis_seq BIGINT` — asOf and basisSeq of StatementEstimated (recorded without checks)
  - `receivable_offset BIGINT, carried_receivable BIGINT, payable BIGINT, run_seq BIGINT` — v1.5: confirmed payout-run values from PayoutCompleted (engine values)
- `policy_limit`:
  - `monthly_premium BIGINT NOT NULL, limit_multiple TEXT NOT NULL, limit_amount BIGINT NOT NULL` — limit_multiple is an opaque decimal string for display only (never multiplied) — since v1.6 V5 the contract's decimalString verbatim (TEXT)
  - `accum_paid BIGINT NOT NULL DEFAULT 0` — stores the engine's accumAfter as is (no portal summation)
- `policy_limit_history`:
  - table comment — ledger timeline (postings and rebases)
  - `tenant_id TEXT, ledger_id TEXT, source_seq BIGINT NOT NULL` — v1.5: key = the event seq of that fact (DD-5 — a REBASE may have no posting sequence number)
- `notification`:
  - `dedup_key TEXT NOT NULL, title TEXT, body TEXT, link TEXT` — v1.9 V7: title and body may only be NULL (killed columns — the server does not build sentences); link has the format of the four screen paths
  - `params JSONB NOT NULL DEFAULT '{}'` — v1.9 V7: a closed shape per type (contract 1.2.0 Notification variants) — no amounts; sentences come from the screen templates (§6.5)
  - `PRIMARY KEY (tenant_id, notification_id), UNIQUE (tenant_id, agent_id, dedup_key)` — v1.2 Q1: dedup_key is unique per agent
  - comment — v1.9 V7: index (tenant_id, agent_id, created_at DESC).
- `inquiry`:
  - comment — v1.9 V7: CHECK — calc_id required except for OTHER, message 1–1,000 code points (not whitespace only), answer 1–4,000, answered_by = `cli:<OS 사용자>`,
  - comment — answer columns per status (OPEN: all three NULL · ANSWERED: all three present · CLOSED: either of the two). Index (tenant_id, agent_id, created_at DESC) — list and 24-hour window.
- `simulation_log`:
  - `error_code TEXT, engine_status INTEGER` — v1.8 V6: failure reason (a portal reason or an engine code, format `^[A-Z][A-Z0-9_]*$`) and the engine HTTP status
  - comment — v1.8 V6: index (tenant_id, agent_id, created_at) — rate-limit window. CHECK: columns per status — REQUESTED has all result columns NULL,
  - comment — SUCCEEDED has response and rule_snapshot_id and no error_code, FAILED has error_code and no response or rule_snapshot_id.
- `ingest_cursor`:
  - `updated_at TIMESTAMPTZ NOT NULL, error TEXT` — updated_at = time of the last apply, stop or operator command (idle polling does not change it)
  - `caught_up_at TIMESTAMPTZ` — v1.6 V5: last time a round received the feed and ended with last_seq = headSeq and RUNNING — the basis for §7 SYNC_DELAYED
- `ingest_event`:
  - table comment — idempotency key + apply audit
- `recon_result`:
  - `source_seq BIGINT NOT NULL` — v1.5: EVENT = that event's seq, BATCH = 0 (confirm and payout DRIFTs at the same time are told apart by the key — Q2)
- `recon_run`:
  - table comment — v1.10 V8: reconciliation round record (one row per tenant × round start time). Zero value columns — only counts and closed values (no amounts, agents or months)
  - `tenant_id TEXT, run_at TIMESTAMPTZ` — run_at = round start = the run_at of that round's DRIFT rows (Q5)
  - `actor TEXT NOT NULL` — `recon:<tenant>` for SCHEDULE, `cli:<OS 사용자>` for CLI
- `audit_access`:
  - table comment — audit of viewing others' data and of administrative actions
  - `detail TEXT` — v1.6 V5: denial reason code (AUTHZ_DENIED — closed values; holds no data values)

**Reconciliation tables** (v1.10 — portal Phase 7): only `DRIFT` · `REPAIRED` are written to `recon_result` — `MATCH` · `INCONCLUSIVE` have no rows and remain only as metrics (`recon_checks_total` · `recon_inconclusive_total`) and the counts in the round row. A row carries the portal's three values (pre-tax, clawback offset, corrections) and only the engine fields **that were compared** — fields not compared (the clawback offset, absent from a confirmed answer) and `engine_net` (after-tax is not compared — plan Q3) are NULL, and there is no column holding a difference or ratio (`ReconResultWriteIT`). A `BATCH` DRIFT row and that month's `data_status = DRIFT` are one transaction; an unresolved DRIFT with the same values is not written again (Q13). `recon_run` is one row per round (inserted with a provisional result at R3 and finalized at R6) — counts and closed values only, and `CHECK` ties together actor, counts, the rebuild pair and the result: `result = FAILED` ⇔ `rebuild_outcome = FAILED`, `REPAIRED` ⇒ `SUCCEEDED` (`ReconRunSchemaIT` — V9 fixed V8 letting NULL through with `IS NOT DISTINCT FROM`). The enum value `MATCH` of `recon_result.status` remains but is not used.

**Status columns**: the canonical value sets are the comments above, and the V1 `CHECK IN` must match them (`StatusEnumDdlTest`). If `simulation_log.status` (REQUESTED/SUCCEEDED/FAILED) needs a failure reason, do not add values; add an `error` column in a new V (in the same commit as the §5 revision — Phase 0 acceptance review F4).

**Ingestion table** (v1.4, V3): `ingest_event` keeps only applied events (`APPLIED`) and poison events whose key can be trusted (`FAILED`) — skipped events have no row (a row with the same `eventId` already exists, so a second row is impossible under the PK; the enum's `SKIPPED` is **unused** and appears only in the metric `ingest_events_total{result=skipped}` — Phase 1 approval Q2). `payload_hash` is the JCS SHA-256 of `{type, version, occurredAt, aggregate, payload}` (excluding `seq`, `eventId` and unknown top-level fields — Q1); the payload body is not stored.

**RLS** (v1.2): every tenant table (including `tenant`) has `ENABLE` + `FORCE ROW LEVEL SECURITY` and the policy `tenant_isolation USING/WITH CHECK (tenant_id = current_setting('app.tenant_id', true))` — canonical `V2__rls.sql`. When unset the setting is `NULL`, so queries return 0 rows without error, and `tenant` too shows only its own row. platform-spring's `TenantSessionBinder` sets the tenant at transaction start with `SELECT set_config('app.tenant_id', ?, true)` (transaction-local — same effect as `SET LOCAL`). The setting name is the public constant `TenantSession.SETTING_NAME`, and `RlsSettingNameTest` checks it against the migration strings (because a misspelling would make the policy always read `NULL` and silently return 0 rows). The app role is an ordinary role that cannot bypass RLS, and V2 asserts that every portal role is `NOBYPASSRLS` and `NOSUPERUSER` (§9 role table). The migrator also obeys the policies (`FORCE`), so **data migrations (backfills, rebuild swaps) enumerate the tenants and run per tenant after setting `set_config('app.tenant_id', …, true)`** — without the setting they see 0 rows and writes are rejected by `WITH CHECK` (Phase 0 acceptance review B1; to be revisited in the §6.7 rebuild design).

**Invariants (continuously verified by tests)**
- `earnings_month.gross/clawback_offset/adjustment = Σ earnings_line` (per tenant, agent and month).
- Ledger (v1.5 — the engine's `LimitLedger.post` rule, **DD-8**): **after a positive posting** `accum_after ≤ limit_amount`; after every posting `accum_after ≥ 0`, `accum_after − 직전 accum_after = amount` (직전 = previous), and `postingSeq` = previous + 1. A rebase (`LimitLedgerRebased`) does not change the accumulated amount and has no checks — after a downward rebase, `accum_paid > limit_amount` is normal (Appendix A-3). On violation, applying the event fails → ingestion stops; this signals an engine anomaly.
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
- **Operator commands** (v1.4, `portal-app` `cli` profile — a single command without web or polling; exit codes 0 success, 2 rejected, 64 usage): `ingest status [--tenant=T]`, `ingest resume --tenant=T --from-seq=n` (`FAILED`/`PAUSED` → `RUNNING`, cursor set to n — n may only be at or below the last `APPLIED` seq; skipping ahead is possible only through a rebuild), `ingest pause --tenant=T` (`RUNNING` → `PAUSED(OPERATOR_PAUSE)`), `ingest rebuild --tenant=T` (v1.5 — §6.7, exit code 1 = rebuild failed). The actor is the single OS user who ran the command (`user.name`) — `--actor` was removed in v1.6 (using it yields 64). Every invocation is written to `audit_access` in the same transaction — rejections included (`INGEST_STATUS`, `INGEST_RESUME`, `INGEST_RESUME_REJECTED`, `INGEST_PAUSE`, `INGEST_PAUSE_REJECTED`, v1.5 `INGEST_REBUILD`, `INGEST_REBUILD_REJECTED`, `INGEST_REBUILD_SUCCEEDED`, `INGEST_REBUILD_FAILED` — a rebuild writes one row per transaction, `actor_subject` = `cli:<actor>`, `target` = `cursor:<T>@<n>`). Without an actor there can be no audit, so the command is rejected before it touches the DB. **Authorization** (v1.6 — portal Phase 3 D-9): `cli:<OS 사용자>` must be present in **that tenant's** `identity_link` as `OPERATOR` and `ACTIVE` (checked first, per command and per tenant). Otherwise `T not authorized` + exit code 2, and `AUTHZ_DENIED` in that tenant (detail `NO_LINK`, `REVOKED` or `ROLE`; target `cli:ingest <명령>` (명령 = command)). A `status` without a specified tenant checks every tenant and returns 2 if any one is denied. Registration and removal are done with operations SQL (`docs/운영.md` §3). Access control on the operations host remains the first gate. v1.9 (portal Phase 6 Q28): there is one runner (`CliRunner` — two groups, `ingest …` and `inquiry …`, same authorization, same exit codes), and three inquiry commands were added — `inquiry list --tenant=T [--status=…]` (tab-separated, including the body — the only place an operator reads the body; control, format and separator characters are written as visible `\uXXXX`, leaving no characters for the terminal to interpret), `inquiry answer --tenant=T --id=<uuid> --answer-file=<path>` (answers **only via a file** — strict UTF-8, 16 KiB, 1–4,000 code points; an `--answer` argument gives 64; in one transaction `OPEN → ANSWERED` + notification `INQUIRY:<id>` + audit; the output is one line), `inquiry close --tenant=T --id=<uuid>` (`OPEN` · `ANSWERED → CLOSED`). Audit `INQUIRY_LIST` · `INQUIRY_ANSWER` · `INQUIRY_ANSWER_REJECTED` · `INQUIRY_CLOSE` · `INQUIRY_CLOSE_REJECTED` (target `inquiry:<id>`, zero body in detail), rejection target `cli:inquiry <명령>` (명령 = command). Tenant-wide statements are in `InquiryOperatorRepository` (the notification inbox API does not reach them — §9 ⓖ). Usage in `docs/운영.md` §7.

### 6.2 Status mapping (engine → screen)

| Engine fact | `earnings_month.status` | `net_basis` (v1.5) | Screen label | After-tax display |
|---|---|---|---|---|
| Lines only | PROVISIONAL | NULL | **Estimated** | "After-tax amounts shown after the overnight calculation" |
| `StatementEstimated` received | ESTIMATED | ESTIMATE | **Estimated** (shows the reference time `estimate_as_of`) | Estimated withholding tax and estimated take-home amount. The basis seq (`estimate_basis_seq`) is a lower bound — even if lines arrive after it, the status stays ESTIMATED |
| `StatementConfirmed` received | CONFIRMED | CONFIRM if tax amounts are included, otherwise the previous value is kept (ESTIMATE or NULL) | **Confirmed** — if `net_basis = ESTIMATE`, "Confirmed (after-tax estimated)" | Engine v1 does not carry tax or net amounts in close events — the estimated values are left as they are |
| `PayoutCompleted` received | PAID | PAYOUT | **Paid** (payout date) | Confirmed withholding tax and actual payout amount (`paidAmount` = `net`), confirmed payout-run values `payable`, `receivable_offset`, `carried_receivable`, `run_seq` |

State transitions follow only the order above (v1.5 — `MonthTransitions`): lines, estimates or a second confirmation for a closed month (CONFIRMED/PAID), and a second payout for PAID, are poison (`CLOSED_MONTH`); a payout before confirmation is poison (`STATE_MISMATCH`). A confirmation creates the row even for a month without lines (an agent with only corrections — engine E1 acceptance Q1). If the totals of a close or payout event differ from the portal sum, this is not poison; instead the month gets `data_status = DRIFT` + `recon_result(source EVENT)` (§6.6).

**Screen labels** (v1.7 — portal Phase 4, Phase 3 acceptance review Q1 · Q2): the wording is in `web/src/messages.ko.json` (data), the table in `web/src/labels/labels.ts`. Every amount is drawn only together with a label (`<Amount value label>` — an amount without a label is a type error). The label of pre-tax, clawback offset and line amounts is the month status (estimated · estimated · confirmed · paid); **corrections are "지급 반영 미정" (payout inclusion undecided)** (the month's correction sum, `byType.ADJUSTMENT`, `ADJUSTMENT` lines — independent of the month status; v1.7.1 — engine E1-2: corrections are not read by close or payout runs — they are in `StatementConfirmed.adjustment` but not in `PayoutCompleted`. Only the wording changes on the day the engine includes corrections in payouts). After-tax (net received) follows the 16 cells below (4 contract enum values × 3 `netBasis` + absent — exhaustively in `labels.test.ts`):

| Month status | `netBasis` | After-tax |
|---|---|---|
| PROVISIONAL | absent | Hidden — "세후는 야간 산출 후 표시" (after-tax shown after the nightly calculation) (pre-tax shown large) |
| ESTIMATED | ESTIMATE | **예상** (estimated) + "기준 {estimateAsOf}" (as of {estimateAsOf}) |
| CONFIRMED | ESTIMATE | **예상** (estimated) (the provisional statement's value — pre-tax is "확정" (confirmed)) |
| CONFIRMED | absent | Hidden — "세후는 지급 시 확정" (after-tax is confirmed at payout) |
| CONFIRMED | CONFIRM | 확정 (confirmed) |
| PAID | PAYOUT | **지급완료** (paid) + payout date |
| The other 10 cells | | Hidden — "세후 정보 없음" (no after-tax information) (combinations the contract does not send — fail towards not showing) |

Withholding tax is shown with that label only when after-tax is shown. Ledger amounts (limit, cumulative, remaining, cut) are "원장 기준" (per the ledger), the monthly premium is "계약 정보" (policy information), deferral and receivable amounts carry the status of that item, and the payout run's confirmed values are "지급완료" (paid) only in PAID months.

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
- Cuts/deferrals: lines with `earnings_line.limit_cut_amt > 0` are summed per policy and shown as `cut_total` (CUT) and `deferred_total` (DEFER_AFTER_FY), and the deferred portion is linked to `deferral_item(reason=LIMIT_DEFER)`. The sums are stored on the ledger row when `CommissionCalculated` is applied (v1.5 — integer summation). **The current engine actually cuts `DEFER_AFTER_FY` as well (engine E1-1)** — `deferred_total` is merely a classification by the policy value recorded on the insurance policy. Screen wording (v1.7 — Phase 3 acceptance review Q4): not the policy name but the fact, **"한도 초과 삭감"** (cut for exceeding the limit), and for `DEFER_AFTER_FY` the supplementary wording "(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)" (company rule: deferral after the first year — currently no deferral schedule). The two totals are not added together; each is shown only if it is non-zero. If the engine implements deferral, only the wording changes.
- For policies concluded before 2026-07-01 the engine opens no ledger, so the screen shows them as "policy not subject to the limit (by contract date)". The portal does not decide this branch — it shows it only from the fact that there are no ledger events.
- The days remaining until `fy_end`, the rebase (REBASE) history and the policy status badges (`policy_flag`) are shown together.
- If a `REINSTATE` (engine REVIVE) arrives without an open flag, it is not poison; nothing is done (v1.5 — Phase 2 acceptance review Q6: it has not been confirmed whether the engine can emit a reinstatement without a lapse, so it is accepted conservatively).

### 6.4 Deferral forecast rules (the boundary of the only "calculation" the portal performs)

- Base forecast = monthly `Σ deferral_item.amount where status ∈ {SCHEDULED, RELEASED}`. HELD is shown in a separate color; CANCELLED is excluded.
- v1.5 (Phase 2): HELD never becomes RELEASED again (engine transitions — §6.2, **DD-9**) — the Phase 4 screen wording is to be chosen so that HELD amounts do not look like "money that can still be received". Items with `reason = LIMIT_DEFER` (limit deferral) are included in the same total (they follow the same state rules as `CURVE`). Phase 2 only creates `deferral_item` rows; totals and toggles come with the Phase 4 queries.
- **Scenario toggle**: "all policies retained" (default) / "assumed retention rate p%" — the monthly totals are multiplied by p for display. The feature is implemented in v1, but because **the tenant parameter `forecast_scenario_enabled` defaults to false** (confirmed, §14.0), the toggle is not exposed on screen and the API rejects `scenario=RETENTION` requests with 400. For tenants that enable it, a "not confirmed, for reference only" notice is fixed on screen. The implementation is isolated in a single class, `ForecastScenario`, and code outside this class that multiplies an amount by a ratio is forbidden by an architecture test.
- v1.8 (Phase 5) **actual scope of the isolation**: portal amounts are `long` in the view records, so "amount multiplication" cannot be singled out by type (plan P-4). Therefore **all multiplication, division and remainder in the portal product bytecode** is blocked, and only an allowlist of three methods is opened (`MultiplicationScanTest` — JDK 25 Class-File API): `ForecastScenario#apply` (`floor(expected × p / 100)` — **the only place that multiplies an amount**), `LimitStage#of` (bp comparison — Phase 3 D-13), `AgentQueryRepository#lines` (page × size). Arithmetic `*` · `/` · `%` in SQL is limited to four fragments converting threshold % → bp (`SqlArithmeticScanTest`). RETENTION changes **only the monthly `expected`** and also carries the value before application as `expectedBase` — `held` and items keep their original values (plan Q8 · Q23). `p` is an integer 1–100 (required only for RETENTION — otherwise 400); with `p = 100` the values equal `ALL_INFORCE`.
- v1.8.1 **meaning of a month row**: `months[]` carries only months whose scheduled (SCHEDULED + RELEASED) or held (HELD) sum is non-zero (the `HAVING` of `dueMonths` — contract 1.1.1). A month with only cancellations has the same shape as a month without items (no row), and its items remain in `items[]` as "취소" (cancelled). The bar denominator and the rule for omitting `bar` are computed only over the months carried (acceptance review B1).
- v1.8 **bar ratio**: per month, `bar {expected, held}` = the display string of the value carried in the response / `max(expected + held)` within the range (platform `Ratio` — the same convention as `utilization`; no division appears in portal bytecode). Omitted if the maximum ≤ 0. The range limit is **84 months** for this path only (period toggle 12/24/48/84 — other range APIs use 36); the policy filter `policyKey` is resolved within that agent's set of deferral-item policy numbers (the same convention as `/lines` — an unknown key gives an empty result; the server produces monthly totals and bars for that policy only).
- Renewal (retention) commissions have no schedule in the engine, so they are not included in the forecast. The screen carries the wording "only confirmed deferral schedules are reflected".
- Whether the 2027 deferral scheme applies to each product line (press reports suggest some products may be excluded) is decided by the engine's curve data. The portal merely shows `deferral_item` rows when they exist and shows nothing when they don't.

### 6.5 Notification rules

Notifications are derived in the **same transaction** as the projector apply, and `dedup_key` prevents duplicates. **`dedup_key` is unique per agent** (v1.2 Q1 — unique key `(tenant_id, agent_id, dedup_key)`; the key strings below do not include the agent, since the column already holds it).

**Derivation point** (v1.9 — RM-4): the applier (`IngestApplier`) calls, inside the event transaction, projector → derivation (`JdbcNotificationDeriver`) → cursor, in that order. There is no deriver that reads the cursor separately. An exception in derivation is not a projector rejection but an `APPLY_ERROR` of that event (no `FAILED` row — views, notifications and `ingest_event` roll back together, the cursor stops — plan Q16). Rows are `INSERT … ON CONFLICT (tenant_id, agent_id, dedup_key) DO NOTHING`, key = name-based UUID (`테넌트 ∥ U+001F ∥ 설계사 ∥ U+001F ∥ dedup_key` (tenant ∥ U+001F ∥ agent ∥ U+001F ∥ dedup_key)), `created_at` = the apply clock (not the engine's `occurredAt` but the time the portal learned of it — Q29) — the rows are the same even if the same stream is fed in pieces across restarts (`NotificationDerivationIT`). Thresholds are read from the tenant settings only for postings (the same statement as the query card).

v1.9 (portal Phase 6 plan ③ — DD-18: the table up to v1.8 did not state the `postingSeq` key, the severity pairs, the DRIFT condition or the recipient of events without an `agentId`): the rules are the pure function `portal-projection` `notify.NotificationRules` — (event, the `Change` returned by the projector, thresholds) → candidates `{agentId, type, dedupKey, params}`. The severity is determined by the type, and the screen path by `params`. `Change` is sealed (`LedgerPosted` · `LedgerRebased` · `LineAdded` · `DeferralChanged` · `FlagObserved` · `MonthClosed`) — it carries the rows the projector already read and saved as they are (zero additional reads). "After" is the row after applying this event; `{seq}` is the feed `seq`.

| Trigger event | Condition | `type` | Severity | dedup_key | `params` | `link` | Recipient |
|---|---|---|---|---|---|---|---|
| `LimitLedgerPosted` | stage(after) > stage(before) — before = the accumulation of the ledger row the projector read, after = `accumAfter`, same limit amount, same tenant bp (the same `LimitStage` as the query card). Even if two or more boundaries are crossed at once, **one, the after stage** | `LIMIT_WARN` · `LIMIT_CRIT` · `LIMIT_FULL` | INFO · WARN · CRIT | `LIMIT_{STAGE}:{ledgerId}:{seq}` (rising again after falling is a new key) | `{ledgerId, policyMasked}` | `/limits/{ledgerId}` | ledger row |
| `LimitLedgerRebased` | always (only this one even if the stage rises because the limit shrank) | `LIMIT_REBASE` | INFO | `LIMIT_REBASE:{ledgerId}:{seq}` | `{ledgerId, policyMasked}` | `/limits/{ledgerId}` | ledger row |
| `CommissionCalculated` | `limitCutAmt > 0` | `LIMIT_CUT` | WARN | `LIMIT_CUT:{calcId}` | `{calcId, policyMasked, closeYm}` | `/lines/{calcId}` | event |
| `CommissionCalculated` | `commType = CLAWBACK` | `CLAWBACK` | WARN | `CLAWBACK:{calcId}` | `{calcId, policyMasked, closeYm}` | `/lines/{calcId}` | event |
| `DeferralStatusChanged` | `to ∈ {HELD, CANCELLED}` (each status occurs once per item — transition table) | `DEFER_HELD` · `DEFER_CANCELLED` | WARN | `DEFER_{to}:{scheduleId}` | `{calcId(원 라인), policyMasked, dueYm}` (calcId of the original line) | `/lines/{sourceCalcId}` | deferral row |
| `PolicyEventObserved` | `eventType ∈ {PAYMENT_OVERDUE, LAPSE, CANCEL}` (`REINSTATE` gives 0) | `POLICY_PAYMENT_OVERDUE` · `POLICY_LAPSE` · `POLICY_CANCEL` | WARN | `POLICY_{type}:{eventId}` | `{policyMasked, occurredOn}` | none (there is no per-policy screen) | event |
| `StatementConfirmed` | after, the month's `data_status ≠ DRIFT` (numbers that do not match are not called "confirmed") | `STMT_CONFIRMED` | INFO | `STMT_CONFIRMED:{ym}` | `{ym}` | `/statements/{ym}` | event |
| `PayoutCompleted` | after, the month's `data_status ≠ DRIFT` | `PAYOUT` | INFO | `PAYOUT:{ym}` | `{ym}` | `/statements/{ym}` | event |
| Operator CLI `inquiry answer` | `OPEN → ANSWERED` | `INQUIRY_ANSWERED` | INFO | `INQUIRY:{inquiryId}` | `{inquiryId}` | `/inquiries/{id}` | inquiry row |
| Re-reconciliation `REPAIRED` (v1.10 — §6.6) | the month was DRIFT and re-reconciliation gives `MATCH` — if that month's status is `CONFIRMED`, confirmation; if `PAID`, both confirmation and payout (the notifications blocked during DRIFT) | `STMT_CONFIRMED` · `PAYOUT` | INFO | `STMT_CONFIRMED:{ym}` · `PAYOUT:{ym}` (**the same keys** as above — if already present, zero rows via `ON CONFLICT`, Phase 6 acceptance Q1) | `{ym}` | `/statements/{ym}` | reconciliation row |

- Re-reconciliation row (v1.10): the rule is `NotificationRules.monthRepaired` of the same pure function; the write is **one transaction** with the `REPAIRED` row and `data_status = OK` (the re-reconciliation transaction — the rebuilder does not call it; ArchUnit `theDerivationRulesHaveAClosedSetOfCallers`: the callers of the derivation rules = the operational deriver, the CLI answer and the reconciliation round). It is the only way for the confirmation and payout notifications of a month blocked by event-reconciliation DRIFT to arise after recovery (§14.1 #12).
- Recipient: ledger and deferral events have no `agentId` — the `agent_id` of the view row (an engine fact from the opening and scheduling events) is used.
- **No sentences or amounts** (v1.9 Q1): the `title` · `body` columns are killed by V7 `CHECK (title IS NULL AND body IS NULL)`, and the sentence is built by the screen template (`messages.ko.json`) from `params`. Why no amounts — so that a past value is not read as the current value after corrections or clawbacks following confirmation, and so that the screen does not get yet another amount label (§6.2). The raw policy number is nowhere in `params`, `link` or `dedup_key` (`policyMasked` only — with `policyKey`, notifications remaining after a secret rotation would carry wrong keys).
- One event can have two candidates (a clawback line that was cut). Each is checked for duplicates separately.
- Tests: `NotificationRulesTest` (every cell of the table, all 16 stage pairs, seeded random posting sequences — stages counted with a boundary-amount oracle), `NotificationStreamsTest` (the real projectors + A-1 and `edges` streams = the plan's hand calculation P-9).

The digest (bundling the same type on the same day into one per day) is v2 (§14.1 #3). The channel in v1 is in-app; push and Kakao AlimTalk (알림톡) are added later as `NotificationChannel` adapters.

### 6.6 Reconciliation (Recon) and DRIFT

- **The first checkpoint is event reconciliation** (v1.5, Phase 2 directive D-3): when `StatementConfirmed` or `PayoutCompleted` is applied, the month's portal sums (gross/offset/adjustment) are compared with the engine totals in the event. A difference is **not poison** — the event is applied (ingestion does not stop), the month gets `data_status = DRIFT`, `recon_result` gets one row with `source = EVENT` and `source_seq` = the event's `seq` (`ON CONFLICT DO NOTHING` — even if a rebuild or re-apply inserts the same row again, there is one row; approval Q2), and the metric `projection_drift_total`. Total fields absent from the event are not compared (the engine fields are optional — contract v1.1). If they match, no row is written. `engine_net` is the event's `net` (NULL if absent). A month that has once become `DRIFT` stays `DRIFT` even if later events match (recovery is through the rebuild and re-reconciliation below).
- Mismatch → `recon_result=DRIFT`, `earnings_month.data_status=DRIFT`. The screen puts a "확인 중" (under review) badge on that month's card and shows the amounts **dimmed** (v1.7: the same for `REBUILDING` of the contract enum — lighter colour only + dotted underline, AA contrast kept. Numbers that may be wrong are not shown as if they were confirmed).

**Nightly reconciliation** (v1.10 — portal Phase 7, `source = BATCH`, `source_seq` = 0). Module `portal-recon` (independent of Spring and DB; ports `ReconStore` · `ReconEngine` · `ReconLock` · `ReconRebuild` · `ReconMetrics` · `Pacer` · `Tracing` — implemented in `portal-app`). The verdict is the pure function `rule.ReconRules` (`ReconRulesTest` — the whole table), the round is `ReconRunner`:

- **Targets**: the scheduler uses the window {current month, previous month} (KST — Q12); the operator CLI `recon run --tenant=T [--ym=YYYYMM,…]` (at most 6) uses the given months. All `earnings_month` rows of those months (agent × month — the engine is not called for "all agents").
- **Round lock** (P7-4): throughout the round, the outer tenant transaction holds `pg_try_advisory_xact_lock(hashtext('portal.recon'), hashtext(tenant))` (only one even with two instances — if it cannot be acquired, no round; CLI exit 2). Saving and rebuilding use new transactions (`REQUIRES_NEW`) — one more connection is used throughout the round (`docs/운영.md` §8 pool size).
- **Verdict** (only `==` on three integers — zero difference or ratio calculation; `MultiplicationScanTest` · `SqlArithmeticScanTest` unchanged): ① if the cursor is not `RUNNING`, or there is no `caught_up_at`, or round start − `caught_up_at` > `recon_caught_up_within_seconds` → `INCONCLUSIVE(LAGGING)` (zero calls) ② when the budget (`recon_max_calls` · `recon_max_seconds`) is exhausted, `BUDGET` (zero calls) ③ mode = month status (`PROVISIONAL` · `ESTIMATED` → `ESTIMATE`, `CONFIRMED` · `PAID` → `CONFIRMED`), `recon_call_interval_ms` between calls ④ an engine failure answer → that reason (`AGENT_NOT_FOUND` · `NOT_CONFIRMED` · `MONTH_CLOSED` · `ENGINE_REJECTED` · `ENGINE_UNAVAILABLE` · `ENGINE_CONTRACT`), an answer of another shape → `ENGINE_CONTRACT` ⑤ **ESTIMATE**: `basisSeq` > the pinned `last_seq` → `LAGGING`; if clawback offset and corrections are equal, `MATCH`, otherwise `INCONCLUSIVE(ESTIMATE_DIFF)` — **pre-tax is not looked at and there is no DRIFT** (Q1 ⓑ′: the engine's estimated pre-tax includes deferral installments falling due that have not been issued yet — measured in `estimate_cycle_05`, **DD-19** — and `basisSeq` is a lower bound. An open month is looked at again as CONFIRMED in a later round) ⑥ **CONFIRMED**: if pre-tax, corrections and (if present in the answer) clawback offset are equal, `MATCH`; otherwise the feed head is read once (not a retry — Q2): if it is empty, `ENGINE_UNAVAILABLE`; if head > `last_seq`, `LAGGING`; otherwise `DRIFT`.
- **Writes**: `MATCH` · `INCONCLUSIVE` → zero rows (metrics only). `DRIFT` → one transaction for that month (row + `data_status = DRIFT`); if the seven values equal the last row of that month's unresolved DRIFT, only `data_status`, without a row (Q13). If a month with an unresolved DRIFT is `MATCH` at the first verdict, it becomes `REPAIRED` on the spot (P7-2 — without a rebuild).
- **Unresolved, new DRIFT, attempted** (Q14): unresolved = a DRIFT row of that (agent, month) later than its last `REPAIRED` (regardless of source — including event-reconciliation DRIFT). New DRIFT = a month that had no unresolved DRIFT at round start becomes DRIFT. Attempted = there is a `recon_run.rebuilt_at` after the **first** DRIFT of that episode (P7-3).
- **Automatic recovery**: (new DRIFT ≥ 1 or untried unresolved ≥ 1) and `auto_rebuild_enabled` and zero rebuilds today (KST) → rebuild (§6.7, actor `recon:<tenant>`, audit `INGEST_REBUILD*`). If the rebuild is rejected (lock, state), it does not count as an attempt. On success, re-reconcile **only the unresolved months** (Q4): `MATCH` → `REPAIRED` row + `data_status = OK` + the §6.5 re-reconciliation notification (one transaction), `DRIFT` → DRIFT again (without a row if the values are the same), `INCONCLUSIVE` → **zero rows**, but `data_status = DRIFT` (approval condition A — the rebuild remade `earnings_month` from events and erased the BATCH mark, **DE-28**; numbers that could not be checked are "확인 중" (under review)). A DRIFT that remains even after the rebuild becomes attempted and is **not rebuilt again from the next day on** (an inconsistency that needs a correction on the engine side — including EVENT DRIFT in the stream itself).
- **Round result** (`recon_run.result`, precedence `FAILED` > `DRIFT` > `REPAIRED` > `OK`): rebuild failed → `FAILED` (zero re-reconciliation); if unresolved ones remain at the end, `DRIFT`; if none after a successful rebuild, `REPAIRED`; otherwise `OK`. Metrics `recon_runs_total{result}` · `recon_unresolved_drift`.
- **Zero retries** (engine calls), concurrency 1 (in order within the round — `ReconRunIT` sees a maximum of 1 concurrent request at the stub); engine unavailable, 404 and 422 are neither a stop nor a DRIFT.

### 6.7 Rebuild

v1.5 (Phase 2 directive D-11, plan ⑥, approval Q10): **a single-transaction replay, not a shadow schema.** The view tables are shared by tenants (multi-tenant), so a `RENAME` swap is impossible, and PostgreSQL MVCC already provides "queries keep seeing the existing rows during a rebuild". With no DDL, there is no FORCE RLS or `portal_migrator` problem either. `ingest rebuild --tenant=T` (§6.1 operator commands) consists of three transactions — the directive's "cursor `REBUILDING` → replay inside one transaction" would make the state merely decorative, since a `REBUILDING` set in the same transaction is invisible to everyone until commit (**DE-9**).

1. **txA**: tenant lock (`pg_try_advisory_xact_lock` — same key as the worker; if it cannot be acquired, reject and audit) → check that the cursor state accepts `OPERATOR_REBUILD` (only `RUNNING`, `PAUSED`, `FAILED`) → commit `REBUILDING`. From then on the worker does not poll (the applier also sees `NOT_RUNNING` inside its transaction), and `resume`, `pause` and `rebuild` are rejected.
2. If the `headSeq` of the first feed page (`afterSeq=0`) > `portal.ingest.rebuild-max-events` (default 50,000), the rebuild does not start; go to txC.
3. **txB** (one transaction — atomicity): lock (retried briefly, because the apply transaction of a worker that read `RUNNING` just before txA may hold it for a moment) → DELETE from the 8 view tables (`agent_profile`, `earnings_line`, `earnings_month`, `policy_limit`, `policy_limit_history`, `deferral_item`, `clawback_item`, `policy_flag`) → replay from seq 1 up to the `headSeq` from step 2 (a head that grows during the replay is not followed — this is the premise for the cap bounding the length of txB; the rest is picked up by the worker from the cursor once it is `RUNNING`. If the replay count reaches the cap, it fails — a double safeguard. If that `headSeq` is lower than the highest seq already `APPLIED`, it fails before deleting, and it also fails if the feed ends before reaching that seq — if the cursor stood below `APPLIED` rows, the worker would skip them as known events and they would be missing from the views forever) — with the **same checks** as the applier (`EventCheck`: receive validation, hash, comparison with existing `APPLIED` rows, seq rule); a row already `APPLIED` must have the same hash and seq, and if it does, the projector is called again; if it is absent or `FAILED`, it becomes `APPLIED` → full invariant check (month total = Σ lines — including lines that have no month row; ledger accumulation = Σ postings; posting chain `accum_after − 직전 = amount` (직전 = previous)) → cursor `RUNNING`, `last_seq` and `last_event_id` = the last replayed event → commit.
4. Failure (poison, hash mismatch, seq discontinuity, invariant, feed error, cap): txB is rolled back (views, `ingest_event` and `recon_result` all return to their original state) → **txC** sets the cursor to `FAILED(REBUILD_FAILED: {원인} seq n)` (원인 = cause), leaving `last_seq` as is. After the cause is fixed, the rebuild can be run again from `FAILED`.

- **Not deleted**: `ingest_event` (the reference for hash comparison), `ingest_cursor`, `audit_access`, `recon_result` (DRIFT history — even if the replay recreates the same row, `ON CONFLICT DO NOTHING`), and user data (`tenant`, `identity_link`, the read state of `notification`, `inquiry`, `simulation_log`).
- **What is restored** (v1.11 — portal Phase 8 ①, Phase 7 F11): after the replay and before the full invariant check (inside txB), `recon_result` is **only read**, and `earnings_month.data_status` is set back to `DRIFT` for the (agent, month) pairs that have an unresolved DRIFT row (`ViewReset.restoreDriftMarks` — one statement). Unresolved = there is no `REPAIRED` row for the same (agent, month) after that DRIFT row (including at the same timestamp) (regardless of source — the same rule as the reconciliation's `Episodes`; the 259-cell grid of `DriftMarkAgreementIT` ties the two together). The rebuilder does not reach the reconciliation module and does not derive (ArchUnit unchanged). Without this invariant, after an operator rebuild that month looked "normal" until the next round (DE-28).
- **Stream source**: re-fetched from the feed — the portal does not store payloads (Phase 1 D-1). The feed calls are inside txB (a long transaction — §14.1 #11).
- Notification derivation (§6.5) is not invoked during replay (settled in v1.9 — portal Phase 6 C3). `Rebuilder` has no place to pass a deriver, and it does not depend, **even transitively**, on the derivation port, implementation, rules or applier (ArchUnit `theRebuilderReachesNoDerivation`). `notification` · `inquiry` are outside the list of view tables deleted, so their bytes are the same before and after a rebuild (including `read_at` — `RebuildIT`). The price (plan Q25): a rebuild replays up to `headSeq`, so it may apply events past the cursor for the first time (when rebuilding from `FAILED` or when the head is ahead), and **no notifications arise for such facts** (`docs/운영.md` §7).
- Audit: start, rejection, success and failure, one row each inside the transaction of the respective step. Metric `ingest_rebuild_seconds{outcome}`.
- Automatic rebuild (v1.10 — §6.6): the reconciliation round calls the same rebuilder (an instance dedicated to reconciliation — its transactions are always new transactions and do not join the outer transaction of the round lock). The actor in the audit is `recon:<tenant>` (distinct from an operator's `cli:<OS 사용자>`), once a day (KST) per tenant. The rebuilder does not depend on reconciliation (ArchUnit — reconciliation calls the rebuild, not the other way round).
- **Determinism** (D-12): from the same stream, the 9 view tables (including `recon_result`, excluding the tenant column, including `source_seq` and `last_seq`), `ingest_event` and `ingest_cursor` are byte-identical across ① a single pass, ② a restart midway, and ③ a single pass + rebuild (`IngestDeterminismIT`).

---

## 7. Query API (portal → web)

Self endpoint paths contain **no** `agentId`. v1.8: contract 1.1.0 (additions and relaxations only — structural comparison with the 1.0.0 copy, `PortalApiCompatibilityTest`; the Phase 3 response validation tests run unchanged against 1.1.0). v1.9 (portal Phase 6): contract 1.2.0 — only the addition of the five notification-inbox and inquiry paths (structural comparison with the 1.0.0, 1.1.0 and 1.1.1 copies; the existing paths and schemas are byte-identical apart from descriptions). v1.11 (portal Phase 8): contract 1.3.0 — only the addition of one unauthenticated `GET /auth/config` (structural comparison with the 1.0.0–1.2.0 copies, §9 browser login). It belongs to the closed list of "unauthenticated paths" outside the permission table (`docs/authz-matrix.md` — `PublicPathsTest`). The agent is identified only by the token subject → `identity_link` lookup, which structurally eliminates IDOR. **Every statement** in the query repository (`AgentQueryRepository`) carries `agent_id = :agentId` (including each table in a join — the second predicate of the SQL scan). The canonical contract is `contracts/api/v1/portal-api.openapi.yaml` (v1.6 — owned by the portal; web Phase 4 generates the client), and every test response is validated against that contract.

| Method/path | Response summary |
|---|---|
| `GET /api/v1/me` | Own ID and role (`identity_link`), name, grade, organization and status (`agent_profile` — **the agent's own name is not masked**; omitted if there is no row — v1.6 DD-13), tenant display parameters (name, payout-day rule, warning thresholds in bp), v1.8 `features {forecastScenario}` (tenant `forecast_scenario_enabled` — included in the ETag settings fingerprint) |
| `GET /api/v1/me/earnings?from=YYYYMM&to=YYYYMM` | Monthly summary time series (gross/clawbackOffset/adjustment/withholdingTax/net/netBasis/paidAmount/paidAt/status/monthDataStatus). `from` and `to` required, `from ≤ to`, span of at most 36 months (otherwise 400) |
| `GET /api/v1/me/earnings/{ym}` | Month summary + totals by type (`byType` — SQL integer sums) + confirmed statement and payout values + expected payout date (tenant rule `NEXT_MONTH_D{n}`; omitted with a warning if n does not exist in that month or the format differs — §14.1 #2) |
| `GET /api/v1/me/earnings/{ym}/lines?page&size&commType&policyKey` | Line list — **flat** (v1.6); reversal pairs are linked by `reversalOf`/`reversedBy`, and folding is done by the screen (Phase 4). Sorted by `source_seq, calc_id`, `page` starts at 0, `size` 1–200 (default 50). Filter `policyKey` (not the policy number — see below). **404** if that agent has no month row for that month (the same resource decision as `/earnings/{ym}` — v1.6.1, Phase 3 F5) |
| `GET /api/v1/me/earnings/lines/{calcId}` | Line detail: basis (basis — the opaque string as is), limit decision (limitCut), policy summary, this line's deferrals and receivables, the limit ledger ID of that policy (inquiry button is Phase 6) |
| `GET /api/v1/me/limits?status=WARN\|CRIT\|FULL\|ALL` | List of per-policy limit cards (remaining limit = SQL integer subtraction, `utilization` display string — omitted when the limit is 0, stage = integer comparison against the tenant thresholds in bp, open flags) |
| `GET /api/v1/me/limits/{ledgerId}` | Limit detail + history timeline (in `source_seq` order) |
| `GET /api/v1/me/deferrals?from&to&scenario&p&policyKey` | Sums by expected payout month (scheduled = SCHEDULED+RELEASED, held = HELD — SQL integer sums) + items. v1.8: range 84 months, `scenario` (`ALL_INFORCE` default · `RETENTION`) · `p` · `policyKey`, response `scenario` · `p` · `months[].expectedBase` · `bar` — §6.4. v1.8.1 (contract 1.1.1): months whose scheduled and held are both 0 are not in `months[]` (they are in `items[]`) |
| `POST /api/v1/me/simulations/new-contract` / `/policy-event` | v1.8 (contract 1.1.0): the engine result with the copy's property values as they are + portal facts `asOf` · `dataStatus` · `simId` (log row key) · `requestedAt` (the row's `created_at`). Only the new-contract result has the engine's `generatedAt` (not in the cancellation-impact result — P-2). Request body `application/json` ≤ 2 KiB (the controller reads only up to the limit + 1 byte — it does not take in an oversize body whole), amounts and counts as numeric strings, the agent from the caller, the policy as `policyKey` (resolved only within that agent's set of line policy numbers — otherwise 404, zero rows and calls). No ETag, `Cache-Control: no-store`. Rejections §9 |
| `GET /api/v1/me/notifications?unreadOnly&page&size` | v1.9 (contract 1.2.0): notification list `{items[], page, size, total, unreadCount}` — sorted `created_at DESC, notification_id DESC`, `size` 1–100 (default 20), `unreadCount` is the total unread count regardless of the filter. Item = `oneOf` of six variants + `discriminator: type` (the shapes of the §6.5 table) `{id, type, severity, createdAt, readAt?, link?, params}` — **no sentences, amounts, raw policy numbers, `dedupKey` or `agentId`** (sentences come from the screen templates; a contract test checks for zero `Won` references in `params`). `link` is a screen path string (`/lines/{calcId}` · `/limits/{ledgerId}` · `/statements/{ym}` · `/inquiries/{id}`, keys percent-encoded — contract `ScreenLink` regex = V7 `CHECK`); policy-change notifications (`POLICY_*`) have no link |
| `PATCH /api/v1/me/notifications/{id}/read` | v1.9: mark as read — one statement `read_at = coalesce(read_at, 지금)` (지금 = now) (idempotent — the second call is also 200, `readAt` keeps the first value), 200 `Notification`, no request body. No ETag, `no-store` |
| `POST /api/v1/me/inquiries` | v1.9: submit an inquiry (dispute) — request `application/json` ≤ 4 KiB `{category, calcId?, message}` (`calcId` required if `category` = `AMOUNT` · `LIMIT` · `CLAWBACK` · `DEFERRAL`, optional for `OTHER` — it must be that agent's line, otherwise 404 and zero rows; `message` 1–1,000 **code points**, not whitespace only, NUL and unpaired surrogates rejected). 201 `{id, status: OPEN, category, calcId?, createdAt}` — the body is not echoed back. No ETag, `no-store`. Limits §9 · §10 |
| `GET /api/v1/me/inquiries?status&page&size` | v1.9: list `{items[{id, status, category, calcId?, createdAt, answeredAt?}], page, size, total}` — **no body or answer** (plan Q23), `status` = `ALL` (default) · `OPEN` · `ANSWERED` · `CLOSED` (anything else is 400) |
| `GET /api/v1/me/inquiries/{id}` | v1.9: detail `{id, status, category, calcId?, message, createdAt, answeredAt?, answer?}` — the **only response** in which an inquiry body appears (to its author only). The answering operator (`answered_by`) is not included |
| `GET /api/v1/org/…` | v2. Org manager's view of subordinates. Authorized by `org_path` prefix match; access is recorded in `audit_access` |

Common fields in every response: `asOf: {seq, at}` (cursor `last_seq` and `updated_at` — with no cursor, `seq` is 0 and `at` is omitted), `dataStatus: OK|SYNC_DELAYED` (**sync** status — delayed if any of: cursor state ≠ `RUNNING`, no `caught_up_at`, `now − caught_up_at > sync_delay_seconds`; also delayed during a rebuild). Month responses give that month's **reconciliation** status separately as `monthDataStatus: OK|DRIFT` — up to v1.5 a single field `dataStatus: OK|DRIFT|SYNC_DELAYED` mixed the two meanings (v1.6 **DD-12**). Amounts are integer won; currency formatting is the frontend's job. Optional fields are left out when they have no value (`null` is not sent).

**The policy number is in no response** (v1.6): `policyMasked` (`*` except the last 4 characters) and `policyKey` = the first 16 hex characters of HMAC-SHA256 (deployment secret file `portal.query.policy-key-secret-file`, `tenant_id` + `U+001F` + `policy_no`). A hash without a secret can be reversed by enumeration because the policy number format is narrow (**DE-15**). Key → original is resolved within the request only against **that agent's** set of policy numbers (no key in SQL — another agent's key yields an empty result). Rotating the secret changes every key — the screen does not store keys (`docs/운영.md`). The insured is the engine-masked `insuredMasked` as is.

**Rejections and formats** (v1.6, §9): every authentication failure is the same 401; no link, `REVOKED`, role (`MANAGER`, `OPERATOR`), another agent's (or a nonexistent) resource, nonexistent path, method mismatch and path variable format errors (`ym`, key) are all the same 404; only query parameter format and range errors are 400 `INVALID_REQUEST`. The body is `application/problem+json` `{code, message}`.

**Caching** (v1.6, §10): 200 carries `Cache-Control: private, no-cache` (not `no-store` — so that 304 means something) and a weak ETag `W/"<seq>.<sync>.<cfg>"` (`sync` = `O`/`D`, `cfg` = the first 8 characters of the SHA-256 of the tenant settings that response actually read — payout-day rule, thresholds, delay seconds; the line list appends `.<page>.<size>.<필터 해시 8자>` (an 8-character filter hash)). Even at the same `seq`, the ETag changes when the delay status or settings change (**DE-16**). v1.10 (**P7-15**): two **data marks** are added to the `cfg` input — the cursor's `updated_at` (a rebuild rewrites the views at the same `seq`; the cursor is written only on apply, failure and rebuild, not on empty polls) and the tenant's last `recon_run` row (`run_at` · `result` · `drift_unresolved` · `rebuild_outcome` — reconciliation changes a month's `data_status` at the same `seq`). The marks are one statement separate from the settings and cursor statements (`SyncStatusRepository.lastRecon` — moved because of the reconciliation SQL arithmetic scan and the query-path repository rules). `If-None-Match` is compared only after the ownership check (another agent's resource gets 404, not 304). v1.9: the three notification and inquiry GETs change with reads, answers and closings regardless of the feed position, so they use a **body ETag** `W/"<응답 바이트 SHA-256 앞 16자>"` (the first 16 characters of the SHA-256 of the response bytes) (contract `BodyETag`, plan Q22) — the same `private, no-cache`, 304, and comparison after the ownership check. The common fields `asOf` · `dataStatus` are not in notification and inquiry responses (they are not feed facts).

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

**Contract requests** (v1.7 — portal Phase 4. **Applied in v1.8 contract 1.1.0** — CR-3 is only `ruleVersionId`: the regulation reference date is in no engine event (plan P-20). CR-4 is the product key, product name and insured of the policy's **first line** (minimum `source_seq` · `calc_id`) — ledger events have no product (P-19, a `LATERAL` subquery in the same statement). CR-1 is a `LEFT JOIN` in the month statement, so the statement count stays the same):

| # | Request | Why |
|---|---|---|
| CR-1 | `MonthResponse.previous` (optional): last month's `closeYm` · `status` · `gross` · `net?` · `netBasis?` — **original values only, no difference** | §8 "compared with last month" — the screen does no subtraction or comparison. The two amounts side by side, each with its label |
| CR-2 | `Line.reversalOfYm` · `reversedByYm` (optional) | So that the line detail need not be called again just to learn the month of a reversal pair in another month |
| CR-3 | `Ledger.ruleVersionId` · regulation reference date (optional) | The "regulation reference date" of the §8 limit formula |
| CR-4 | `Ledger.productName` · `productKey` · `insuredMasked` (optional) | Product name and insured on the §8 limit card — not in the ledger response |

---

## 8. Screen design (mobile first, text wireframes)

**Common**: a sync status chip at the top (`기준 09-22 10:15` (as of 09-22 10:15) / `동기화 지연` (sync delayed) / `확인 중` (under review)). A status label next to every amount (Estimated · Confirmed · Paid). Integer won, with thousands separators.

**Implementation conventions** (v1.7 — portal Phase 4, `web/`):
- Paths (closed list `web/src/routes.ts` = server `ScreenConfiguration.SCREEN_PATHS`): `/`, `/statements`, `/statements/{ym}`, `/lines/{calcId}`, `/limits`, `/limits/{ledgerId}`, v1.8 `/deferrals` · `/simulations` (lazy chunks — `bundle-size` checks the closed list), v1.9 `/notifications` · `/inquiries` · `/inquiries/new` · `/inquiries/{id}` (all four lazy chunks; the static segment `new` is in the server list as is). There is no policy number or `policyKey` in paths (the timeline policy filter is a query parameter; the hand-over to surrender impact is one slot in memory).
- **This month** = the KST calendar month of the browser clock (time zone explicit). If there is no row for that month (404), "이번 달 내역이 아직 없습니다" (no statement for this month yet) — only a 404 on `/me` means "등록되지 않은 사용자" (unregistered user) (the same 404 bytes). v1.8: last month is the month response's `previous` (CR-1) — last month's pre-tax (and after-tax, if shown) side by side with that month's label, no subtraction; if absent, "전월 내역 없음" (no statement for last month). Next month's deferrals are the `expected` of one slot of `/me/deferrals` ("분급 예정" (deferral scheduled)); if there is no month row or it is 0, "다음 달 예정 없음" (nothing scheduled next month). Recent notifications (v1.9): the three items of one call `/me/notifications?size=3` (the fifth home call) + the unread count (`unreadCount` as is); if none, "새 알림이 없습니다" (no new notifications); entry points to the notification inbox and inquiry history. Policies needing attention = the number of ledgers whose stage ≠ OK; payment overdue / lapse = the number of `OVERDUE` · `LAPSED` flags (one call `/limits?status=ALL` — `status=WARN` means exactly WARN only).
- Sync chip: the responses drawn on the screen are combined towards the worse — if any one is `SYNC_DELAYED`, "동기화 지연 — 기준 {MM-DD HH:mm}" (sync delayed — as of {MM-DD HH:mm}); the reference is the `at` (KST) of the smallest `seq`; if there is no `at`, "동기화 전" (not yet synced).
- Statements: the month list is the months with rows among 36 months. Lines: the whole month is fetched (pages up to `total`) and **only pairs within the same month** are folded (the original line under "취소된 원 라인 보기" (show cancelled original line)). Pairs in another month are not folded but get a "취소됨 → {월}" (cancelled → {month}) / "취소 ← {월}" (cancellation ← {month}) badge + a link to the other month (v1.8: the other month comes from the line's `reversalOfYm` · `reversedByYm` — no additional call; if absent, "다른 달의 짝" (pair in another month) without a link). Payout-run values (payout amount, receivable offset, carried-over receivable) only in PAID months. Expected carried-over receivables are not shown (§14.1 #13).
- The label of correction amounts (the corrections row of the month card, corrections in the per-type totals, `ADJUSTMENT` lines — list and detail) is "지급 반영 미정" (payout inclusion undecided) in any month (§6.2 — v1.7.1). Corrections in a PAID month are not "지급완료" (paid) either.
- A confirmed month with corrections only (no lines, or only `ADJUSTMENT`, corrections ≠ 0): "정정 {금액} — 지급 반영은 정산팀 확인" (correction {amount} — payout inclusion to be confirmed by the settlement team) (not asserted — §14.1 #12). The amounts in it are also "지급 반영 미정".
- Limits: the attention/all filter is applied on the screen (stage string). Cards and detail show the product name (if absent, `productKey`) and the masked insured (v1.8 CR-4 — the policy's first line; omitted if absent). The utilization bar does not turn the `utilization` string into a number but uses it as a CSS property. The formula is the display string "월납 {월납} × {배수}배 → 한도 {한도액}" (monthly premium {premium} × {multiple} → limit {limit}) (no regulation reference date — CR-3).
- The screens do not calculate, compare or convert amounts — opaque `Won` type, ESLint rules, bundle scan (`CLAUDE.md` screen conventions).
- v1.9 **Notifications**: the sentences are screen templates (`messages.ko.json` `notifications.<TYPE>`, fourteen — no server sentences, no amount slots; months in month format, dates as is, policy numbers as the masked value). Unread is bold + a dot + the hidden text "안읽음" (unread). Tapping sends the read `PATCH` (only if unread) → navigates to `link` **only if it matches** one of the four closed screen paths (`/lines/:calcId` · `/limits/:ledgerId` · `/statements/:ym` · `/inquiries/:id` — down to the segment shape), otherwise only marks as read (plan approval A — server data is not trusted: external origins, `//`, unknown paths, queries and dot segments are rejected). The inbox is newest first + "더 보기" (more) (next page); there is no "mark all read".
- v1.9 **Inquiries**: the form `/inquiries/new[?calcId=]` (the line detail's "이 항목 문의하기" (ask about this item) passes that line's key, the monthly statement passes none — without a key only the category "기타" (other) is available), categories as a group of toggle buttons, the fixed text "고객 이름·연락처·증권번호를 쓰지 마세요" (do not write customer names, contact details or policy numbers), the remaining character count in code points (`aria-live=polite` — over the limit it is not sent), the rules from the contract-generated rules file. After sending, the screen is replaced by the detail (`replace`); 429 is "오늘 문의 한도를 넘었습니다 — {Retry-After}초 뒤 다시" (today's inquiry limit exceeded — try again in {Retry-After} seconds) and the input stays in memory. The list shows category, status label (접수됨 (received) · 답변 완료 (answered) · 종료 (closed)) and submission time; the detail shows body, answer, answer time and the line link — the body appears on screen only in the form and the detail. The Phase 4 "준비 중" (coming soon) entry points no longer exist (component deleted).
- v1.8 Timeline: period 12/24/48/84 months (from this month) · policy filter (policy of a limit card → `policyKey` query) · retention-rate assumption toggle (only when the tenant has `features.forecastScenario`; `p` is blocked up front by the contract `pattern`). The bar width uses the `bar` string as a CSS property, and the bar's accessible name is the cell of the table below (`aria-labelledby`). Labels: the all-in-force and before-assumption totals "분급 예정" (deferral scheduled), only the assumption-applied total "참고(유지율 {p}% 가정)" (reference (assuming a {p}% retention rate)), held "보류" (held) + legend "지급 조건 미충족으로 멈춘 분급 — 예정 합계에 넣지 않음" (deferrals stopped because payout conditions are not met — not included in the scheduled total), items carry the item status.
- v1.8 Simulation: two tabs (`role=tablist`, left/right arrows). Inputs are kept as strings and blocked up front by the rules file generated from the contract (`web/src/gen/portal-api.rules.json` — written by `client:lock`, its hash pinned by the lock; `pattern`, length, required, `if/then`). Every amount in the result carries the "시뮬레이션" (simulation) label, the fixed text, the rule snapshot, request number (`simId`), request time and (new contract) the engine's calculation time. No utilization rate or before/after difference is produced. The last result is kept only in screen memory and stays through 429 ("잠시 후 다시 시도해 주세요({Retry-After}초)" (please try again shortly ({Retry-After} s))), 503, 502 and 422. The insurer input field appears only after a 422 `INSURER_AMBIGUOUS`. Five change types (no `PAYMENT_OVERDUE`). No dark mode (`color-scheme: light` — also turns off the webview's forced inversion), Korean only.

1. **Home**
   - This month's card: expected/confirmed take-home pay (large number) · pre-tax · clawback offset · withholding tax · expected payout date D-n · status label
   - One line comparing with the previous month (on a confirmed basis)
   - Policies needing attention: N with limit WARN or above, M with late payment/lapse warnings → go to Limit status
   - One line with next month's expected deferral amount → go to the timeline
   - 3 most recent notifications
2. **Limit status**
   - Filter (attention/all) · policy card: product name (if absent, product code `productKey` — the engine has no policy master, v1.3) · insured (masked; omitted if absent) · contract date · utilization bar (%) · remaining amount · first-year end D-n · policy status badge
   - Detail: limit formula (monthly premium × multiplier, regulation reference date) · ledger timeline (postings, rebases) · cut/deferral breakdown (linked deferral items) · "Impact if this policy is surrendered" button (→ the simulation's surrender tab with that policy selected — v1.8)
3. **Deferral timeline**
   - Period toggle (12/24/48/84 months) · monthly bars (split into scheduled/due for payout/held) · scenario toggle (all policies in force / retention rate assumption) · policy filter
   - v1.8.1: the months of the bars and table are the API's `months[]` as is — a month with only cancellations has no row and appears only as "취소" (cancelled) in the item list below (the screen does not filter out zeros)
   - Fixed footer text: "Reflects confirmed deferral schedules only · For reference · Not final"
4. **Simulation**
   - Tab ① New-policy estimate: product selection · monthly premium · contract date (default today) → expected amount per first-year installment · limit and expected utilization · whether incentives are included · deferral schedule for the first year/after
   - Tab ② Surrender impact: policy selection · point in time → clawback amount · change in this month's take-home pay (before/after) · deferral items that get canceled · receivable balance. v1.8: the engine's value is the pre-tax + clawback-offset total of the month the clawback is attributed to (`closeYm`), so it is shown as "{월} 세전·환수 상계 합계 — 변경 전 / 변경 후" ({month} pre-tax and clawback offset total — before / after) and is not called "실수령" (take-home) (that month may not be this month)
   - Fixed text: "For agent reference only · Not final · Not to be shown to customers" · the rule snapshot ID is shown on results
5. **Monthly statement**
   - Month selection · totals by type → line list (policy · type · amount · status · reversal pair) → line detail (applied rate · payout rate · installment number — only what the engine provided; if absent, that row is omitted (v1.3), limit decision, linked items) → "Ask about this item"
6. **Notification inbox / settings**: mark as read, digest settings (if the tenant allows).

Accessibility and field use: minimum touch target 44px, large numbers first, low-end devices considered (bundle size cap checked in CI — v1.7: initial load gzip ≤ 127 KiB, measured 118,149 B + 10%; v1.8 measured 121,056 B (CI) — zero new dependencies), no screen-capture prevention (it is the agent's own data). v1.7: zero axe WCAG 2.1 AA violations, keyboard access on every path with visible focus, zero transitions and animations under `prefers-reduced-motion` — E2E on three viewports (mobile Chromium and WebKit, desktop).

---

## 9. Security, privacy and multitenancy

- **Authentication**: OIDC. A per-tenant IdP (the adopting GA's SSO) or the portal's default IdP. Development and demo use Keycloak (Phase 8); tests use an in-process issuer (RSA key + JWKS stub). **The tenant is determined by the token's `iss`** (v1.6): `tenant.oidc_issuer` (UNIQUE, exact match) and `oidc_audience` — if both are NULL the tenant cannot log in. The issuer table must be read **before** binding the tenant, so it is read on every request with the dedicated role `portal_issuer` (role table below) (no cache — revocation takes effect immediately). `/api/**` verification order (portal Phase 3 plan ②; all yield **the same 401 bytes** `{"code":"UNAUTHENTICATED","message":"authentication required"}` + `WWW-Authenticate: Bearer` — the reason goes only to metrics): ① exactly one `Authorization: Bearer` header (none, another scheme, or more than one → `NO_TOKEN`) ② JWT format, string `iss` and `sub` (1–200 characters) → `MALFORMED` ③ header `alg` = `RS256` only → `ALG_NOT_ALLOWED` ④ `iss` → registered tenant → `ISSUER_UNKNOWN` (only registered issuers get their metadata and JWKS fetched — no outbound request to a URL chosen by the token) ⑤ signature against that issuer's JWKS (one decoder per issuer URL) → `SIGNATURE` (footnote v1.6.1 — Spring `Jwt` rejects `exp ≤ iat` in its constructor, so the decoder throws the same exception, and the metric reason for such a token is `SIGNATURE`, not `EXPIRED`. The response is the same 401 bytes — Phase 3 F9) ⑥ `exp` (required) and `nbf`, 60-second tolerance, injected clock → `EXPIRED` ⑦ `aud` contains that tenant's `oidc_audience` → `AUDIENCE` ⑧ **if** a `tenant_id` claim is present, it is the same tenant (cross-check — not a basis of trust) → `TENANT_MISMATCH`. If the JWKS cannot be fetched, the result is 500, not 401 (a server-side failure is not hidden by blaming the token — with 401 the client would keep repeating the login), metric `api_jwks_failures_total{issuer_hash}` (first 12 characters of the SHA-256 of the issuer URL, v1.6.1 — Phase 3 F6), retries are up to the client. The only things read from the token are `iss`, `sub`, `aud`, `exp`/`nbf` and `tenant_id`; the `roles` and `agent_id` claims are read nowhere — the only portal class that touches `Jwt` is the verifier. Roles (team leader, branch manager, etc.) are not taken from engine events either — the engine has no concept of roles (override recipients are organizations, not people), and `AgentSnapshot.role` is optional (v1.3).
- **Token in the screens** (v1.7 — portal Phase 4 Q1): the webview host opens `/{경로}#token=<JWT>` (경로 = path). The first script, before React, moves the fragment into memory and removes it with `history.replaceState` (a fragment is not carried in requests or the Referer). Zero browser storage and cookies (ESLint + E2E). On reload the host puts it in again. No token or 401 → the "다시 로그인" (log in again) screen (the portal has no login screen or IdP address; "다시 시도" (try again) = reload), `/me` 404 → "등록되지 않은 사용자 — 소속 GA에 문의" (unregistered user — contact your GA). A standalone browser uses the browser login below (v1.11). The webview host's renewal convention is §14.1 #14 (closed in v1.11).
- **Browser login** (v1.11 — portal Phase 8 ③, plan Q2 · P-9 · P-11 · P-13, conditions A · B): a screen opened without a token asks `GET /auth/config` (unauthenticated, contract 1.3.0) once. The server picks the tenant only by an **exact match** between the request origin (`Origin`, or scheme + `Host` if absent — browsers do not send `Origin` on same-origin GETs) and `tenant.web_origin` (V10, UNIQUE, `^https?://[^/]+$`), and returns only two fields, `{"issuer","clientId"}` (`Cache-Control: no-cache`). An unregistered origin gets the same bytes and the same headers as the 404 of the remaining chain — origins cannot be enumerated (`AuthConfigIT` · `UniformRejectionIT`). The read is `IssuerDirectoryReader.byWebOrigin` (the same role `portal_issuer`; V10 added a SELECT on two columns — the direct-access allowlist still has one class, the statement allowlist +1), with a 60-second in-memory cache per origin (injected `Clock`, absence is cached too, at most 256 entries — on an unauthenticated path the request determines the key). On 404, the current webview flow as is ("다시 시도" (try again)). If there is a configuration, the "로그인" (log in) button → **Authorization Code + PKCE (S256)**: the popup (a new tab on mobile) is opened first on the click (to avoid blocking), then discovery (exact issuer match; authorization, token and end-session endpoints within the issuer origin) → `state` · `code_verifier` (32 random bytes each, base64url) → authorization request. The popup, at `/auth/callback` (a screen path — `SCREEN_PATHS` · `routes.ts`, not the unauthenticated path list), clears the address with `replaceState` before React, hands `code` · `state` without rendering them to a `BroadcastChannel` named `pkce:<state>`, and closes. The main window accepts **only one** message with the same `state` on its own attempt's channel and closes the channel (mismatched, second, and earlier attempts' messages are discarded). Token exchange, refresh and end-session are `fetch` calls to the issuer origin (no cookies) — the network lint exception is the single file `src/auth/oidc.ts` (a closed pair with the generated client `src/api/client.ts`; the storage ban is unchanged). 60 seconds before expiry (plan Q3), the refresh token is used to renew and **is replaced by the new refresh token** (rotation). On a renewal failure or an API 401, the token and refresh token are discarded and the login screen is shown. An ID token, even if received, is not kept. Logout (button only in PKCE sessions): memory is cleared first, then the refresh token is POSTed to the end-session endpoint. `state` · `code_verifier` · `code` · access and refresh tokens are all module memory — zero use of the five browser storages, cookies and `window.name` (ESLint, Vitest `pkce.test.ts`, compose E2E). A new tab or a reload means logging in again.
- **Screen response headers** (v1.7 — Q2 · Q6): same origin, the screen chain of `portal-app` (unauthenticated — the authentication filter is only on the `/api` chain). CSP `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'` (a native webview is not a frame), `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`. Screen paths serve `index.html` (`no-store`), `/assets/*` are hashed names (`immutable`), everything else is the same 404. API response headers are as in v1.6. v1.11: if a tenant with a login configuration is registered for the request origin, only `connect-src` of the screen responses (documents and assets) is `'self' <발급자 출처>` (issuer origin) (reassembled from the scheme, host and port of the issuer URL — not added if it does not fit the format), and the other nine directives are the same. An unregistered origin gets the ten directives above as they are (`WebScreensIT`).
- **Authorization**: the caller (`Caller` = tenant, `agent_id`, role) is built only from a single `identity_link` PK lookup (no cache) (v1.6). Roles are the closed set `AGENT` (agent's own data), `MANAGER` (organization view — v2; for now rejected on `/api/v1/me/**`) and `OPERATOR` (subject of the operations CLI — no `agent_id`, rejected on `/api`). No link, `REVOKED`, disallowed role, and another agent's (or a nonexistent) resource are all **the same 404 bytes** `{"code":"NOT_FOUND","message":"not found"}`, and the reason goes only to the audit (`audit_access` action `AUTHZ_DENIED`, `target` = path template, `detail` = closed reason code `NO_LINK`, `REVOKED`, `ROLE`, `MISSING` — no values, **in a transaction separate from the request itself**) and to metrics. Nonexistent paths under `/api`, method mismatches and path variable format errors are the same 404 too (no audit); only query parameter format errors give 400 `INVALID_REQUEST`, the single exception. A 401 has no audit row — a request whose tenant is unknown has nowhere to write a row, and forged tokens carrying only a registered `iss` could fill the audit table (write amplification). Self endpoints query only by the `Caller`'s `agent_id`. The organization view (v2) uses org path prefix match + role check, with every access recorded in `audit_access`. **The org path has two sources** (v1.6): `agent_profile.org_path` is an engine fact (`AgentSnapshot` — for display) and `identity_link.org_path` is a portal fact (registered by operations — for authorization, the scope of the v2 organization view). Even when they differ, neither is corrected (which one to follow is a v2 decision — §14.1 #9).
- **Three-layer tenant isolation**: ① a request filter binds `TenantContext` to the tenant of the verified token issuer (v1.6); without authentication, 401 ② the repository layer allows only `TenantScopedRepository` — architecture tests (ArchUnit + SQL source scan) forbid queries without a `tenant_id` condition ③ PostgreSQL RLS. `TenantContext`, `TenantSessionBinder` and `TenantScopedRepository` come from platform 0.2.0 (v1.2); rejecting DB access outside a transaction and failing to start a transaction without a context are guarantees of the platform, but portal tests (`TenantContextGuardTest`, `TenantSessionBinderTest`) prove them again. Each entry on the direct-access allowlist must satisfy all three conditions — closed FQN list + dedicated role + no access to tenant data — and the list was empty in Phases 0–2. The first entry, in v1.6 (portal Phase 3), is `com.ga.portal.infra.directory.IssuerDirectoryReader` — the issuer table is tenant configuration and must be read before the tenant is bound (dedicated role `portal_issuer`, a small read-only connection pool for that reader alone).
- **Architecture rules** (v1.2, `portal-app` `archTest` — rule building blocks are platform-core `ArchRules`): (a) dependency direction `app → query/ingest/sim/inbox/recon → projection → domain → platform-core` (v1.9 `portal-inbox`); `infra` and `demo` only from `app` (b) `domain` and `projection` (and platform-core) have no dependency on Spring, `java.sql` or `javax.sql`; of the platform, only core (c) `double`/`float` forbidden in every module (d) DB access only by `TenantScopedRepository` subclasses; raw entry points (`DataSource`, `JdbcClient`, `JdbcTemplate`, …) only by platform classes and the portal allowlist (e) `platform-spring` only from `infra` and `app` (f) 0 portal classes sharing a simple name with a platform-core type (g) connection- and statement-level JDBC types (the `DataSource`, `DriverManager`, `Connection`, `Statement` families) only in platform classes and the allowlist — the result type (`ResultSet`) is allowed by (d) down to repository subclasses because of `RowMapper` (h) `BigDecimal` forbidden in every portal module. The SQL scan (`TenantPredicateScanTest`) requires, in the main sources of every module — DML in migration `.sql` files and SQL in Java strings — a `tenant_id` condition on every access to a tenant table (including `tenant`) except `flyway_schema_history`, and for Java repository SQL it also requires the `:tenantId` bind (for INSERT, the value in the `tenant_id` position; otherwise `tenant_id = :tenantId`). The allowlist is (file, statement) pairs, each entry with a reason, and it fails if a retired entry remains — the only entry in v1.6 is the issuer reader's `iss` lookup statement (paired with the direct-access allowlist entry above). v1.11: a second statement of the same reader (the `web_origin` lookup — browser login) was added (same file, same role).
- **Query API rules** (v1.6, portal Phase 3): `portal-query` is on the Spring/DB-free list of (b) (both Gradle `verifySpringFree` and ArchUnit). **Second predicate** — every statement of the query repository (closed list: `AgentQueryRepository`) carries `agent_id = :agentId` as many times as there are `FROM`/`JOIN` references to tables with an agent column (including subqueries and joins; allowlist empty). `ApiLayerRulesTest`: ⓐ every `@RestController` handler method takes a `Caller` and calls exactly one `@UseCaseEntry` method ⓑ the classes that see `Jwt` are a closed list (the one verifier), nobody sees `JwtAuthenticationToken`, and controllers do not see Spring Security or `Principal` ⓒ in `portal-query` · `portal-sim` · `domain.access`, the only public methods taking a `Caller` are `@UseCaseEntry` ones (closed exception: `ApiAuthorization.require` — in v1.8 the access conventions moved to `com.ga.portal.domain.access` in `portal-domain` and the authorization table was renamed) ⓓ every entry point calls `ApiAuthorization.require` ⓔ only `CallerArgumentResolver` calls the `Caller` constructor ⓕ the only view repositories reachable from query paths are `AgentQueryRepository` and `SyncStatusRepository` ⓖ (v1.9) the only repositories reachable from the notification-inbox and inquiry paths (`InboxController` · `portal-inbox` · `app.inbox`) are `NotificationRepository`, `InquiryRepository` and `TenantRepository`. The canonical authorization matrix is `docs/authz-matrix.md` — `AuthzMatrixTest` cross-checks document ↔ `ApiAuthorization` ↔ `@GetMapping` ↔ `@UseCaseEntry` in both directions.
- **DB roles** (v1.2, canonical `docker/postgres/init-roles.sql` — every role is `NOBYPASSRLS` and `NOSUPERUSER`, asserted by V2):

  | Role | Purpose | Privileges (GRANT) | Used by |
  |---|---|---|---|
  | `portal_migrator` | Schema owner, migrations | Owns the DB and the `public` schema. Because of `FORCE RLS`, the owner also follows the policies | Flyway (`portal-app` startup, test harness) |
  | `portal_app` | Application data source | `SELECT/INSERT/UPDATE/DELETE` on 18 tenant tables (including v1.10 V8 `recon_run`), `USAGE` on `audit_access_id_seq`, schema `USAGE`, DB `CONNECT`. `NOINHERIT`; no `TRUNCATE`, `TRIGGER`, `REFERENCES` or `CREATE`; no privileges on `flyway_schema_history` | `portal-app` (repositories under platform-spring `TenantSessionBinder`) |
  | `portal_directory` | (v1.4, V3) Tenant list only — the tenants the ingest worker runs for | Only **column** SELECT on `tenant.tenant_id` and the policy `tenant_directory` dedicated to this role (SELECT, `USING (true)`). 0 privileges on other columns or tables and no write privileges, `NOINHERIT` | Platform `TenantDirectoryReader` (`portal-app` worker). Tenant data is read per tenant by binding with `portal_app` |
  | `portal_issuer` | (v1.6, V5) Issuer table only — token `iss` → tenant. (v1.11, V10) request origin → login configuration | Only **column** SELECT on `tenant`'s `tenant_id`, `oidc_issuer` and `oidc_audience` (v1.11 + `web_origin` · `oidc_client_id`) and the policy `tenant_issuer` dedicated to this role (SELECT, `USING (true)`). 0 privileges on other columns or tables and no write privileges, `NOINHERIT` | Portal `IssuerDirectoryReader` (`portal-app` security chain — direct-access allowlist entry) |
  | `portal_health` | (v2 — decided in v1.11, portal Phase 8 plan Q5, not to create it) DB health indicator only | 0 privileges on tenant tables | The DB health indicator stays off (`management.health.db.enabled=false`) — it is not turned on without an allowlist entry and a dedicated role. The demo has no use for it, so the role was not created (§14.2) |
- **Privacy minimization**: the portal stores customer names only in masked form (the engine masks them before publishing) and does not receive resident registration numbers, contact details or addresses. Policy numbers are stored, but **query responses contain no original** (v1.6 — `policyMasked` last 4 characters, `policyKey` HMAC, §7). Logs and metrics carry subject, `agent_id` and issuer URL only as hashes (first 12 characters of SHA-256), and the only metric tags are `tenant` and `reason` (two exceptions: `issuer_hash` of the JWKS failure metric — the issuer URL hash, v1.6.1; `route` of the rate-limit metric — closed values `simulations` · `inquiries`, v1.9). Storage encryption (disk, backups), TLS in transit.
- **Retention**: notifications 1 year, simulation logs 3 years (example value, tenant parameter), audit log 5 years (example value). Lines and statements depend on the engine's retention policy (since they can be rebuilt, the portal's own retention can be reduced to an operationally convenient period).
- **Service-to-service authentication**: portal → engine uses a service token (mTLS in operation). The engine's `/internal/**` is reachable only from the portal network.
- **Rate limiting**: simulations and on-demand statement refreshes are limited to N per minute per agent (parameter). When exceeded, the last result is shown again. v1.8 (portal Phase 5): the check = the number of that agent's `simulation_log` rows (preceding 60 seconds, `REQUESTED` · `SUCCEEDED` · `FAILED` all counted) ≥ N (tenant `params.sim_rate_per_minute`; if absent or malformed, the application default 10) → **429** `RATE_LIMITED` + `Retry-After` (seconds until the oldest row in the window drops out — rounded up, at least 1). The check and inserting the `REQUESTED` row are one transaction under a per-agent advisory lock (`pg_advisory_xact_lock(hashtext('portal.sim'), hashtext(테넌트 ∥ U+001F ∥ 설계사))` (tenant ∥ U+001F ∥ agent)) — even with concurrent requests, at most N pass. A 429 writes no row, no audit and makes no engine call — only the metric `api_rate_limited_total{tenant}` (v1.9: with the tag `route="simulations"`). Recording takes two transactions: before the call `REQUESTED` (the raw engine request — server-internal, never carried into any response or log), after the call `SUCCEEDED` (the validated engine response and rule snapshot) or `FAILED` (reason code and engine status, no body).
- **Simulation rejection bytes** (v1.8, plan Q19 — in addition to the query 401 · 404 · 400 · 500, one body per code, `application/problem+json`): 422 `{"code":"INSURER_AMBIGUOUS","message":"insurer must be chosen"}` · 422 `UNSUPPORTED_EVENT_TYPE` (`event type not simulated`) · 422 `ENGINE_REJECTED` (`rejected by the engine`) · 429 `RATE_LIMITED` (`too many simulations`) · 502 `ENGINE_CONTRACT` (`engine answer not usable`) · 503 `ENGINE_UNAVAILABLE` (`engine unavailable`). Format, size and schema errors in the request body are the same single 400 as for queries. Only the `Retry-After` value differs per request.
- **CSRF** (v1.8): simulation is the first state-changing method (POST), but authentication is only the `Authorization: Bearer` header, with no cookies or sessions (the screens hold the token in memory — §9 token in the screens); it is same-origin and CORS is not opened. There is no path by which a browser would send credentials from a foreign origin, so there is no CSRF token. v1.9: the same rationale applies unchanged to the three write paths (simulation `POST`, mark-as-read `PATCH`, inquiry `POST`) — zero token or `Origin` checks. The test: a `GET` · `PATCH` · `POST` carrying only cookies and no `Authorization` gets the same 401 bytes (`UniformRejectionIT` — the same even with a foreign `Origin`).
- **Inquiry body isolation** (v1.9, portal Phase 6 C7): the body (`message` · `answer`) leaves only via the author's detail response (`GET /api/v1/me/inquiries/{id}`) and the operator CLI `inquiry list` (the operator's terminal — `docs/운영.md`). The request body is read only up to the limit + 1 byte, and its mapper has source-location inclusion turned off (Jackson `INCLUDE_SOURCE_IN_LOCATION` — parse exception messages contain no input fragments); 400 is fixed bytes, logs carry only the exception class name, zero body in metric tags and audit `detail`, and the record's `toString` masks the body. The test sweeps responses, log capture, metrics and audit with a sentinel body (sentinel file `inbox`) (`InquiryIsolationIT`).
- **Inquiry rate limit** (v1.9): number of `inquiry` rows per agent in the preceding 24 hours ≥ N (tenant `params.inquiry_rate_per_day` — a positive integer of 1–9 digits, otherwise the application default 20 `portal.inbox.default-inquiries-per-day`) → **429** `{"code":"RATE_LIMITED","message":"too many inquiries"}` + `Retry-After` (integer seconds 1–86,400 — until the oldest row in the window drops out, rounded up). Line check → advisory lock (`pg_advisory_xact_lock(hashtext('portal.inquiry'), hashtext(테넌트 ∥ U+001F ∥ 설계사))` (tenant ∥ U+001F ∥ agent)) → window check → insert, in one transaction (the check formula is the same `RateLimitRule` as for simulations — only the window differs). A 429 writes no row or audit — only the metric `api_rate_limited_total{tenant, route="inquiries"}`.
- **Rejection bodies** (v1.9 — a closed 11): 401 `UNAUTHENTICATED` · 404 `NOT_FOUND` · 400 `INVALID_REQUEST` · 500 · the six simulation bodies (above) · inquiry 429 `RATE_LIMITED` (`too many inquiries`). Another agent's, a nonexistent or a malformed `{id}` (anything that is not a lowercase UUID) on the notification and inquiry paths gets the same 404 + `AUTHZ_DENIED MISSING` (the same convention as path keys in queries); format, size and schema errors in the request body are the single 400.

---

## 10. Non-functional requirements and operations

| Item | Target | Means |
|---|---|---|
| Projection lag | Engine commit → screen within 60 seconds at p95 | Poller interval parameter (`tenant.params.poll_interval_seconds` 5–30, otherwise the global default of 10 seconds — v1.4), metrics (Micrometer, tag `tenant`): `ingest_lag_seq` (= `headSeq − last_seq`), `ingest_lag_seconds` (seconds elapsed since the last cursor update while behind; 0 once caught up), `ingest_state` (0/1 per state), `ingest_events_total{result=applied\|skipped\|failed}`, `ingest_feed_errors_total`, v1.5 `projection_drift_total` (§6.6) and `ingest_rebuild_seconds{outcome}` (§6.7) — dashboards and alerts are Phase 7 |
| Peak load | 09:00 on the day after close, all agents connecting at once | The SQL statement count of the three home calls is independent of data volume (v1.6 — `/me` 4, `/earnings/{ym}` 5, `/limits?status=WARN` 4 = issuer 1 + link 1 + settings/cursor 1 + body — v1.7: the `/limits?status=ALL` the home screen actually calls is also 4; v1.8: the fourth home call `/deferrals?from=다음달&to=다음달` (다음달 = next month) 5 (body 2), month detail stays 5 with CR-1 in the same statement; v1.9: the fifth home call `/notifications?size=3` 4 (issuer 1 + link 1 + page 1 + count 1 — it does not read settings or cursor); v1.10 (P7-15): calls that use the sync context have one more statement for the reconciliation mark — `/me` 5, `/earnings/{ym}` 6, `/limits` 5, `/deferrals` 6, `/notifications` stays 4; the same with 1,000 lines, 200 ledgers and 500 notifications — `QueryStatementIT`). 304 via a weak response ETag (`seq`, sync status, fingerprint of the settings read, data marks — §7), `Cache-Control: private, no-cache` |
| Screen bundle | Low-end devices and webviews | v1.7: initial load (HTML + entry JS + static imports + CSS) gzip ≤ 127 KiB (measured 118,149 B + 10%); per-path lazy chunks in a size table (`web/scripts/bundle-size.mjs` → `build/web/bundle-size.json`, `./gradlew build` fails if exceeded). v1.8: measured 121,056 B (CI, limit unchanged), the six non-home screens are lazy chunks (closed list — a static import fails), zero new dependencies (no chart library — CSS bars) |
| Number of limit cards | `/me/limits` has no paging (v1.8 — portal Phase 5 plan Q7) | Up to 200 ledgers per agent is the measured size (`QueryStatementIT` — statement count unchanged). If a GA exceeds it, paging in v2 (contract addition) |
| Availability | Queries continue while the engine is down | D-3. Only simulations go into degraded mode (v1.8: 503 `ENGINE_UNAVAILABLE`, zero retries — the screen keeps the last result and queries are unaffected) |
| Simulation rate limit | N per minute per agent (tenant `sim_rate_per_minute`, default 10) | v1.8: row-count check + per-agent advisory lock (§9), 429 + `Retry-After`, `RateLimitIT` (N+5 concurrent → N pass) |
| Inquiry rate limit | N per 24 hours per agent (tenant `inquiry_rate_per_day`, default 20) | v1.9: the same check formula with a 24-hour window, advisory lock (§9), 429 + `Retry-After`, `InquiryRateLimitIT` (N+5 concurrent → N pass) |
| Consistency | Portal totals = engine statement (verified daily) | §6.6 reconciliation. v1.10: metrics `recon_checks_total{tenant, result}` · `recon_inconclusive_total{tenant, reason}` · `recon_runs_total{tenant, result}` · `recon_unresolved_drift{tenant}` (the name `recon_drift_count` used up to v1.9 was never used) |
| Recovery | Rebuild possible from any state | §6.7, determinism tests |
| Observability | Per-tenant dashboards | v1.10: **the canonical source for metrics, log fields and spans is `docs/지표.md`** (closed tables — 20 metrics: 12 up to Phase 6 + 8 in Phase 7; `MetricsCatalogTest` cross-checks them both ways with the registration statements of every module; one set of tag keys per name; zero identifier tags — exceptions `tenant` · `issuer_hash`). Tracing: Micrometer Tracing → OpenTelemetry bridge (in process; export in Phase 8), span port `portal-domain` `trace.Tracing` — attributes are only tenant, counts and kind names (`TracingLeakIT`); Observation is not used (so that no timers outside the table appear). Operational alert channel v1 = metrics + the operator CLI `status --tenant=T|--all` (no values — months only as ages, approval condition B). What follows is the description up to v1.9: structured logs (tenant_id, agent_id hash), OpenTelemetry traces (including portal → engine calls). Query API (v1.6): `api_authn_rejected_total{tenant, reason}` (the eight 401 reasons — `tenant` is that tenant only when the issuer is registered, otherwise `none`: the set of tag keys is always the same), `api_authz_denied_total{tenant, reason}` (404 — `NO_LINK`, `REVOKED`, `ROLE`, `MISSING`) and `api_jwks_failures_total{issuer_hash}` (v1.6.1 — 500 when the metadata or JWKS of a registered issuer could not be fetched). No agent or subject tags. `sub`, `agent_id` and issuer URL in logs are the first 12 characters of SHA-256 (`LogIds`). Simulation (v1.8): `sim_requests_total{tenant, reason}` (`OK` · `ENGINE_UNAVAILABLE` · `ENGINE_CONTRACT` · `INSURER_AMBIGUOUS` · `UNSUPPORTED_EVENT_TYPE` · `ENGINE_REJECTED`) and `api_rate_limited_total{tenant, route}` (v1.9: `route` = `simulations` · `inquiries` — the tag key set of one name must be the same), failure log `SIM_FAILED` (tenant, kind, code, engine status, `simId` — no request values or policy numbers) |
| Deployment | Containers, zero downtime | Principle of forward-compatible Flyway migrations (column drops in two steps) |

---

## 11. Tech stack

| Area | Choice | Notes |
|---|---|---|
| Backend | Java 25 (LTS) + Spring Boot 4.1.x | Same line as the engine. If the engine is on the 3.5 line, the version mismatch is not a problem since it is a separate process. Boot 4 requires Java 17+ |
| Persistence | PostgreSQL 18.x (image digest pinned — `postgres-image` in `gradle/libs.versions.toml`, starting value 18.6) + Flyway + repositories are only subclasses of platform-spring `TenantScopedRepository` (a `JdbcClient` wrapper) | RLS, JSONB. Projectors have no DB dependency (tested with in-memory repositories). Spring Data JDBC repository interfaces are not used because they bypass the path that enforces the tenant predicate (v1.2) |
| Frontend | React 19 + TypeScript + Vite (v1.7: React 19.3, TypeScript 5.9 strict, Vite 8, react-router 8, TanStack Query 5, pnpm 12 — lockfile and `--frozen-lockfile`; Gradle downloads Node 24 LTS) | No chart library (v1.8 — deferral bars are CSS + a table, directive ⑬); state in a server-state cache (TanStack Query). The client is generated from the contract by `openapi-typescript` (generated code committed + contract hash lock). PWA (service worker) in v2 — responses would remain outside memory (v1.7) |
| Authentication | OIDC / Keycloak (development and demo — v1.11 compose 26.8.1) | Spring Security resource server. The screens use Authorization Code + PKCE (§9 browser login, v1.11) |
| Observability | Micrometer Observation → OpenTelemetry (v1.10), OTLP export (v1.11 — `opentelemetry-exporter-otlp`, only when the endpoint property is set — compose only; zero in tests and CI, `OtlpExportOffIT`) → Jaeger. Metrics scrape endpoint `/actuator/prometheus` (v1.11 — `micrometer-registry-prometheus`, management port only, `ManagementPortIT`). No Prometheus or Grafana server | Span keys are in the `docs/지표.md` §4 table |
| Testing | JUnit (version managed by the Boot 4.1 BOM — JUnit 6), AssertJ, Testcontainers (PostgreSQL), ArchUnit / Vitest, Playwright. **jqwik forbidden** (blocked at Gradle resolution) — property tests use `@ParameterizedTest` + `SeededCases` (`platform-core` test fixtures) | §13 |
| Contracts | JSON Schema (2020-12) + OpenAPI 3.1 | `contracts/`, validated in the CI of both repositories. `format` forbidden (`pattern`); the shared core is a copy under `contracts/shared/ga-disclosure/` (§4 contract conventions, v1.2) |
| Build and run | Gradle multi-module (version catalog + dependency locks + verification metadata — SHA-256 for every dependency including plugins and BOMs, SNAPSHOT forbidden), pnpm, docker compose (v1.11: `docker/compose.yaml` — the §3.2 demo configuration. Engine + Oracle are outside compose — `e2e/engine-feed/run.sh`, plan Q6) | Full verification with two commands: `./gradlew build` + `pnpm build` |
| Platform | `com.ga.platform:platform-core`, `platform-spring`, `platform-canonical` **0.2.0** (published by ga-disclosure, GitHub Packages) | v1.2. 0.1.0 is not used. CI resolves only from GitHub Packages (PAT secrets `GHP_USER`, `GHP_READ_TOKEN`), local builds only from `mavenLocal`, and the SHA-256 of 13 jars, POMs and module metadata files is pinned in the verification metadata (`verifyPlatformPins` cross-checks against the table in §6 of the ga-disclosure handoff) |

---

## 12. Implementation roadmap (Claude Code work order)

Each Phase must be independently mergeable and proves its completion criteria with tests. `E-` denotes work in the engine repository.

(v1.11 — **portal Phases 0–8 are all finished** (Phase 8 is merged and tagged `v1.0` after the acceptance review reply). Per-Phase tags, PRs, reports and reviews are in the README "AI 협업 방법론" (AI collaboration methodology) table. The Phase 9 line below has been moved to the §14.2 v2 table.)

- **Phase 0 — Skeleton**: repository, Gradle modules, Flyway, Testcontainers (PostgreSQL), `Won`/`Ym`/`TenantId` value objects, `TenantContext`, RLS session binding, architecture tests, initial schemas under `contracts/`. *Completion criteria: a repository method without a tenant condition fails in tests; 0 rows in a session without RLS set.*
- **Phase E1 — Engine outbox and feed**: §4.4 items 1, 2, 5, 6 + the event feed of 4 (v1.3 — moved forward from E2 so that portal Phase 1 does not end without a real supplier). *Completion criteria: when the engine golden scenario runs, the published event sequence matches the snapshot file and passes schema validation.*
- **Phase 1 — Ingestion**: poller, cursor, idempotency, seq rules, poison event isolation, reprocessing command. *Completion criteria: duplicate/out-of-order/gap/restart/cross-tenant isolation tests pass.* (v1.4: in parallel with engine E1 — the feed is tested with a stub server and a file adapter; one E2E check against the real engine after E1 acceptance)
- **Phase 2 — Projection**: earnings, limit, deferral, clawback and flag projectors, invariants, rebuild, determinism. *Completion criteria: Appendix A scenario event stream → view golden tests; two replays identical.*
- **Phase 3 — Query API, authentication, authorization**: `/api/v1/me/**`, `identity_link`, IDOR tests (querying another agent's calcId gives 404), audit log. *Completion criteria: all authorization matrix tests pass.*
- **Phase 4 — Frontend, first pass**: home, monthly statement, limit status, sync status chip, status label conventions (v1.7: the notification inbox is Phase 6 — entry point only). *Completion criteria: Playwright scenario (shows the Estimated → Confirmed → Paid transitions).*
- **Phase E2 — Engine synchronous API**: §4.4 items 3 and 4 (nightly `StatementEstimated`, two simulation dry-runs, statement query — the feed is E1). *Completion criteria: a test shows that a dry-run leaves nothing in the ledger or COMM_CALC.*
- **Phase 5 — Simulation and deferral timeline**: `portal-sim`, `simulation_log`, scenario toggle (`ForecastScenario` isolation tests). *Completion criteria: contract tests against engine response stubs, rendering tests for the fixed text.* (v1.8: contract `portal-api` 1.1.0, the copy of the engine response samples is the stubs' only data (the engine's `SimulationContractTest` compares it with the engine's responses), V6, rate limit, two multiplication-isolation scans, screens `/deferrals` · `/simulations`, seven E2E scenarios — report `docs/phase-05-보고서.md`)
- **Phase 6 — Notification derivation and disputes**: §6.5 rules, dedup, digest, inquiry API. *Completion criteria: fires only once when crossing a boundary upward; downward and re-upward cases.* (v1.9: contract `portal-api` 1.2.0, V7, `portal-inbox`, derivation inside the apply transaction (RM-4) and zero derivation in rebuilds, the three operator CLI `inquiry` commands, the notification inbox and three inquiry screens, seven E2E scenarios — the digest is v2. Report `docs/phase-06-보고서.md`)
- **Phase 7 — Reconciliation, DRIFT, observability**: §6.6, metrics and traces, operational alerts. *Completion criteria: artificially injected missing line → DRIFT detected → automatic rebuild → REPAIRED.* (v1.10: copy of the engine statement samples (engine `StatementContractTest`), `portal-recon`, V8 `recon_run` · V9, operator CLI `recon run` · `status`, in-app scheduler, `docs/지표.md` and tracing, five E2E scenarios (ⓐ–ⓔ — new tenant GA06), real-engine `run.sh` verdict ⑤. Zero screen or contract changes. Report `docs/phase-07-보고서.md`)
- **Phase 8 — Demo tenant and integrated run**: Appendix B generator, docker compose, README (portfolio perspective), E2E. *Completion criteria: after `docker compose up`, logging in as a demo agent shows data on all 5 screens.* (v1.11: the generator is on the engine side (appendix B — engine `DemoContractTest`; the portal holds a byte copy), the `portal-demo` engine stand-in, compose profile `demo` (§3.2 — secret generation, Keycloak, Jaeger), browser login with PKCE (§9 — closes §14.1 #14, contract 1.3.0 `/auth/config`, V10), OTLP export and Prometheus scrape endpoint (§11), the end-of-rebuild invariant (§6.7 — F11), compose E2E (CI job `compose-e2e` — five screens, notification inbox, inquiries, logout, secret scans), k6 (§13). Report `docs/phase-08-보고서.md`)
- ~~Phase 9 (follow-up)~~ → v2 (§14.2 — org manager view, PDF statements, push/KakaoTalk AlimTalk notifications, offline snapshots, etc.).

When instructing Claude Code, every Phase prompt must include: ① the relevant sections of this design document, ② the conventions "the portal does not calculate amounts (only sums and ratio display), double forbidden, no access without tenant_id, agent_id resolved only via identity_link, no changes to closed-month views", ③ that Phase's golden cases.

---

## 13. Test strategy

| Layer | Target | Approach |
|---|---|---|
| Contract | Event and API schemas | Engine: schema validation of published events (provider). Portal: schema-based fake event generator (consumer). CI byte-compares only the shared contract copy (`contracts/shared/`) against the upstream merge commit (`verifyUpstreamContract`); checksums are plain SHA-256 (`verifyContractChecksums`) — v1.2. Portal: `EventSchemaTest` (17 samples, required-field removal, Envelope ⊇ core, pairs, 0 `format`) |
| Projection | Event stream → view | Golden streams (Appendix A) + property tests (`@ParameterizedTest` + seed-fixed generator `SeededCases`, jqwik forbidden): invariants of §5 hold for arbitrary streams, view unchanged after duplicate insertion, replay determinism |
| Ingestion | Ordering, idempotency, isolation | seq gaps/out-of-order/duplicates, other tenants proceed after a poison event, cursor consistency after restart |
| Authorization and isolation | Tenant and agent boundaries | RLS integration tests (Testcontainers), IDOR tests, ArchUnit and SQL scan. v1.6: authorization matrix ↔ code in both directions (`AuthzMatrixTest` — canonical `docs/authz-matrix.md`), byte-identical 401s and 404s (`UniformRejectionIT`, `AuthzIT`), response scan (all endpoint responses ∩ seeded policy numbers and other agents' IDs = ∅ — `ResponseLeakScanIT`), contract validation of every test response (`portal-api.openapi.yaml`) |
| Reconciliation | DRIFT detection and recovery | v1.10: the whole verdict table (`ReconRulesTest`), round and recovery units (`ReconRunnerTest` — fake ports), real DB and real HTTP stub (copy of the engine samples — 500 outside the samples): `ReconRunIT` (zero rows, one transaction, determinism, Q13, lagging, lock, budget, concurrency 1), `AutoRepairIT` (rebuild → `REPAIRED` with zero additional notifications, an inconclusive re-reconciliation writes zero rows and keeps DRIFT, a DRIFT in the stream itself is attempted only once), `ReconResultWriteIT` (columns and values), `ReconEngineIT`, `ReconRunSchemaIT` (`CHECK`), `ReconSchedulerIT` (fixed clock — one round a day), `StatusCliIT`, `ReconArithmeticScanTest` (zero `long` arithmetic and `Math` addition/subtraction bytecode in reconciliation classes, zero arithmetic operators in `recon_result` · `recon_run` statements — no allowlist, P7-16). E2E: an artificially missing line (harness control `/corrupt/<T>/line/<calcId>` — SQL with the seed role, zero product code) → screen "확인 중" (under review) → automatic rebuild → normal |
| Simulation | Engine round trip, degradation, limits | v1.8: engine stub = copy of the engine samples (JCS comparison — requests outside the samples get 500), round trip per sample (`SimulationPassThroughIT` — the same values down to the integer literals), the engine answer table (`EngineFailureIT` — zero retries), 10 rejection bytes (`SimulationRejectionIT`), authorization (`SimulationAuthzIT` — zero calls with another agent's key), limit (`RateLimitIT` — N+5 concurrent), log rows (`SimulationLogIT`), bytecode scan for amount multiplication and division and SQL arithmetic scan (closed allowlists) |
| Observability | Metrics and spans | v1.10: `MetricsCatalogTest` (static scan of the sources ↔ `docs/지표.md`), `PortalMetersTest` (committed ones only), `TracingLeakIT` (real SDK, in-memory export — names, parents and keys ⊆ the table, zero sentinels or raw stream content in values; the exported spans are written to `build/trace-export/*.log` so that the plaintext scan reads them too — P7-17), `IngestPollingIT` (the app assembly exports spans and creates no metrics outside the table) |
| Frontend | Status labels, fixed text, accessibility, leaks | v1.7: Vitest (the 16 label cells, wording ↔ contract enum in both directions, screen components, lint rules), three Playwright projects (mobile Chromium and WebKit, desktop — real ingest: streams fed step by step into the harness's feed stub) — leaks (DOM, responses, console, address, storage), CSP, axe, 44px, motion, keyboard, bundle limit and output scan (amount arithmetic, inline scripts). v1.8: the new label set differs from the existing labels, input rules file (generated from the contract), forbidden wording (held amounts as money "received again", surrender impact as "실수령" (take-home)), simulation and timeline E2E (engine stub control `/sim/<tenant>/…`, dynamic sentinel `simId`). v1.9: seven notification and inquiry E2Es (E1–E7 — notifications appear only at that point as the head is raised, zero confirmation notifications for DRIFT months, re-crossing and rebase in the hand-written stream GA05, rebuild and answer via the **real CLI** — harness controls `/cli/<tenant>/rebuild` · `/cli/<tenant>/answer/<id>` start the bootJar as a child process with the same command as an operator and return only the exit code, inquiry limit 429), the inquiry body sentinel set (`pii-sentinels.properties` `inquiry` — allowed only in the form and detail screen DOM and the detail response; zero everywhere else and in outputs; a full run requires, per project, that it actually appeared in the detail response) |
| Performance | Concentrated access | k6 scenario for the day after close (N agents × 3 home calls), checking p95. (v1.11 — portal Phase 8, plan P-2 · Q13, DD-21: `perf/k6/home.js` — 30 demo agents concurrently repeat the home bundle (the **five calls** the real home makes, `/me` · `/me/earnings/{이번 달}` (this month) · `/me/limits?status=ALL` · `/me/deferrals` · `/me/notifications?size=3`, issued concurrently as a browser does). **Example values** (the design document had no response-time target — values newly set as pass criteria): p95 ≤ 300 ms per call, bundle p95 ≤ 800 ms, zero failures. Manual workflow `perf.yml` (not a gate — GitHub shared runners, the whole compose stack on the same machine). Result table and run IDs in `docs/phase-08-보고서.md`) |
| Demo and integration | One compose set | (v1.11) CI job `compose-e2e`: image build (public sources only) → `docker compose --profile demo up` → time to first sync → Playwright desktop Chromium (PKCE login with the README account → five screens, notification inbox, inquiries → after logout the IdP asks again; per screen leaks, storage, axe; zero code/state in the callback document; operator CLI `recon run` · `status` · `ingest rebuild` with identical responses before and after; Jaeger span names and keys ⊆ the `docs/지표.md` §4 table) → secret scan (`scan-secrets.sh` — files 0600; zero values in configuration output, logs, inspect, images and outputs; management port not exposed) |

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

(v1.6.1 — Phase 3 acceptance review Q11: the numbered list was turned into a table and an **implementation location** column was added. For the default assumptions of Phases 0–2 this column stands in for code markers `TODO(confirm#N)`, which are not retrofitted. From Phase 3 on, code markers are placed only where there is a decision point.)

(v1.11 — portal Phase 8 plan Q10: this table keeps **only the items still open after Phase 8**. Items closed or moved to v2 (#1 · #4 · #7 · #9 · #10 · #12 · #13 · #14) keep their numbers and their original text in §14.3, and the work remaining for v2 is gathered in one table in §14.2. The numbers of the code markers `TODO(confirm#N)` do not change — the markers remaining in the code now are #2 · #5 · #9.)

| # | Item | Implementation location |
|---|---|---|
| 2 | The actual per-tenant values of the payout-day rule (`payout_day_rule`), and whether business-day adjustment applies. (v1.6 default assumption: only `NEXT_MONTH_D{n}` is interpreted, with no adjustment. If `n` is a day that does not exist in the next month (e.g. February 31) or the format differs, `payoutExpectedOn` is **omitted** and a warning is logged — it is not pulled back to the last day of the month (plan approval Q12). Business-day adjustment and other formats remain open.) | `portal-query` `rule.PayoutDay` — `TODO(confirm#2)` |
| 3 | Actual limit warning thresholds, digest bundling policy. (v1.9 — digest **closed**: no bundling — one row per fact, in-app only (no push, AlimTalk or mail). The bundling policy goes to v2 together with the push channel. The actual thresholds are still the tenant settings' example values 80% · 95%.) | Thresholds: `tenant.limit_warn_pct` · `limit_crit_pct` → `SyncStatusRepository` (bp) → `portal-domain` `rule.LimitStage` (v1.9 — shared by the query card and notification derivation, Phase 6 plan Q12). Digest: none (v2) |
| 5 | Per-tenant differences in masking rules (customer name, policy number); masking at engine publish time vs. at portal display time. (v1.6 default assumption: the customer name is the engine-masked `insuredMasked` as is, and the policy number is masked by the portal at response time as `policyMasked` (last 4 characters) — no per-tenant differences. Per-tenant rules remain open.) | `portal-domain` `rule.PolicyMask` (v1.9 — query responses and notification `params`) — `TODO(confirm#5)`. Customer name is the engine value as is (no portal code) |
| 6 | IdP integration approach (adopting GA's SSO vs. portal IdP), the subject remapping procedure for agents with multiple affiliations or who move. (v1.6 default assumption: tenant = token `iss`, one issuer per tenant, RS256. When a subject changes, the link for the old subject is set to `REVOKED` and a row for the new subject is added — `docs/운영.md` §2. The remapping procedure itself remains open. If one subject exists in two tenants, the issuers differ so each is handled separately — a configuration in which two tenants use the same issuer is not supported (`oidc_issuer` UNIQUE).) | `portal-app` `web.IssuerTenantResolver` (tenant = `iss`), `tenant.oidc_issuer` UNIQUE (V5), remapping procedure `docs/운영.md` §2 |
| 8 | Minimum requirements for a **legacy adapter** for GAs with their own settlement system (which events are mandatory — whether it can run in a reduced mode with at least `CommissionCalculated`, `StatementConfirmed` and `PayoutCompleted`). | None — contract (`contracts/events/v1/`) |
| 11 | **Cost of the long rebuild transaction** (v1.5 — §6.7): txB holds the replay of the whole tenant and the feed calls — meanwhile its snapshot is held, so VACUUM on other tables cannot reclaim dead tuples (bloat), and the deleted view rows and new rows go to WAL all at once. The only locks are that tenant's advisory lock and row locks, so other tenants and queries are not blocked. The cap (`rebuild.max-events`) bounds the size. Tenants above 50,000 need the snapshot bootstrap (#10) or page-wise shadow tables — after measurement (Phase 7 load test). (v1.10: this is why the automatic rebuild runs **at night** — the reconciliation round runs after the tenant's `recon_hour_kst` (default 04:00 KST), and a rebuild happens at most once a day per tenant and not again for an attempted DRIFT. The reconciliation round holds one more connection throughout the round (P7-4 — `docs/운영.md` §8). The load measurement is passed on to the Phase 8 compose.) | Setting `portal.ingest.rebuild-max-events` (default 50,000) → `portal-ingest` `apply.Rebuilder` |

### 14.2 What remains for v2 (v1.11 — portal Phase 8 plan Q10)

Work that portal v1 (Phases 0–8) did not do. The "current default assumption" is the behaviour of v1 — it stays as is until v2 changes it.

| Item | Source decision | Current default assumption | Code location |
|---|---|---|---|
| Org manager view (`/api/v1/org/…`, organization-scoped authorization) | §2 · §7, §14.1 #9 (→ §14.3), §14.0 `org_member_lines_visible` | The role `MANAGER` is in the closed set but has zero open paths (`docs/authz-matrix.md`). The canonical organization source is undecided — `identity_link.org_path` (for authorization) and `agent_profile.org_path` (for display) may differ | `portal-infra` `identity.IdentityLinkResolver` — `TODO(confirm#9)` |
| Settlement staff support console (operations screen) | §2 table | Operations are only the CLI (`ingest` · `recon` · `status` · `inquiry`) and metrics (`docs/운영.md`) | `portal-app` CLI profile |
| PWA (service worker), offline snapshots | §11, DD-16, Phase 9 | None — service workers are blocked by ESLint (screen conventions) | — |
| Push, AlimTalk, mail, digest (bundling) | §6.5, §14.1 #3 (digest — closed in v1.9) | Notifications in-app only, one row per fact | The `NotificationChannel` adapter slot (one, in-app) |
| PDF statements | Phase 9 | None — statement screens only | — |
| Paging of `/me/limits` | §10 table (v1.8 — Phase 5 plan Q7) | No paging — sized for up to 200 ledgers per agent | `portal-query` `QueryStatementIT` |
| On-demand provisional statement refresh | §14.1 #1 (→ §14.3) | Only the engine's once-nightly run | None |
| Engine `OUTBOX_EVENT` retention, current-state snapshot bootstrap | §14.1 #4 · #10 (→ §14.3) | The feed is everything from seq 1 — rebuilds also start from seq 0 | None — engine side |
| Subject remapping procedure (moves, multiple affiliations) | §14.1 #6 | Old subject `REVOKED` + a new row (`docs/운영.md` §2) — the procedure itself is undecided | Operations SQL |
| Display of expected carried-over receivables (`carriedReceivable`) | §14.1 #13 (→ §14.3) | Not on screen — a storage column is needed (V11 candidate — V8 · V9 were used by Phase 7, V10 by Phase 8) | None |
| Dedicated role `portal_health` for the DB health indicator | §9 role table, Phase 8 plan Q5 | The DB health indicator stays off (`management.health.db.enabled=false`) — no allowlist entry or dedicated role | — |
| compose `engine` profile (real engine + Oracle in compose) | Phase 8 plan Q6 | Comparison with the real engine is `e2e/engine-feed/run.sh` (up to the user — `RESULT.md`) | `e2e/engine-feed/` |
| Regenerating the demo stream (moving business dates to the real date) | Phase 8 plan P-20 | Stream business dates fixed at 2026-03 to 2028-02 — on any other date this month on the home screen is empty (no demo-only clock) | Engine `DemoScenario` |
| Engine product master API | §14.1 #7 (→ §14.3, v1.8) | Products come from the limit card's product or direct input — the engine answers for unknown keys and the portal returns 502 `ENGINE_CONTRACT` | Screen `SimulationScreen` |

### 14.3 Open items closed or moved to §14.2 (original text — numbers kept)

| # | Item | Implementation location |
|---|---|---|
| 1 | Whether on-demand provisional statement refreshes are allowed, and the per-agent limit (additional triggers beyond the nightly run). | None — outside the scope of portal Phase 5 (directive ⑬, v1.8). Still open |
| 4 | `OUTBOX_EVENT` retention policy and when to introduce `TenantSnapshot`. | None — engine side |
| 7 | Simulation rate limit values, the source of the product list (whether an engine product master API is needed). (v1.8 — **closed**, portal Phase 5 plan Q3 · Q10: the limit is the tenant's `sim_rate_per_minute` (default 10, a 60-second window per agent); products come from the limit card's product (CR-4 — the policy's first line) and direct input. For a product key the engine does not know, the engine answers and the portal returns 502 `ENGINE_CONTRACT` — an engine product master API is a v2 candidate.) | `portal-domain` `rule.RateLimitRule` (v1.9 — the window is chosen by the caller: simulations 60 seconds, inquiries 24 hours) + `app.sim.JdbcSimulationLogStore` (params), screen `SimulationScreen` (product datalist) |
| 9 | **Org manager v2** (v1.5 — portal Phase 1 and E1 acceptance review §3-1): a new event carrying organization moves and org manager assignments is recommended; not decided now. Until then, organization information lives in `identity_link` (Phase 3). (v1.6: `identity_link.org_path` — a portal fact, for authorization, required except for `OPERATOR`, registered via operations SQL. `agent_profile.org_path` is an engine fact, for display (the organization in `/me`). **The two sources may differ and neither is corrected** — which one is canonical for the org manager scope is a v2 decision. The second edition of A-1 has no `AgentSnapshot`, so a tenant with an empty `agent_profile` is normal (**DE-13**).) | `portal-infra` `identity.IdentityLinkResolver` — `TODO(confirm#9)` |
| 10 | **#4 extension — current-state snapshot bootstrap** (v1.5 — E1 acceptance review §2 §9-5): ledgers from before E1 have no opening events. An engine current-state snapshot job (agents, ledgers, deferrals, receivables) is a candidate for E2 scope — the portal-side consumption (whether to take the snapshot as seq 0 facts) after seeing that plan. | None — outside engine E2 scope (E2-3) |
| 12 | **Notification derivation during replay** and the **screen convention for unpaid corrections** (v1.5): replay does not derive notifications — **default direction** (Phase 2 acceptance review F9): a notification signals a new fact, and a rebuild rewrites facts. Phase 6 settles it. How the screen shows a month in which an `ADJUSTMENT` line is not yet included in a payout run (`StatementConfirmed` for an agent with only corrections — E1 acceptance review Q1) is Phase 4. (v1.6.1 — Phase 3 acceptance review Q3: a `CONFIRMED` month with only `ADJUSTMENT` shows "정정 N원 — 지급 반영은 정산팀 확인" (correction N won — payout inclusion to be confirmed by the settlement team) — not asserted.) (v1.7.1 — Phase 4 acceptance review A1, **RM-3**: the sentence above narrowed this item to months with only corrections. Corrections are not in the payout run in any month, so the label of correction amounts is "지급 반영 미정" (payout inclusion undecided) regardless of the month status — §6.2.) (v1.9 — replay derivation **closed**, Phase 6 plan Q25: a rebuild does not derive. No notifications arise for facts a rebuild applies for the first time past the cursor either — recorded only (`docs/운영.md` §7); whether the re-reconciliation flow fills the gap is a Phase 7 question.) (v1.10 — **closed**: the gap is filled **only within the scope of month notifications** — re-reconciliation `REPAIRED` inserts that month's confirmation and payout notifications with the same keys (§6.5). Facts not tied to a month, such as ledger, deferral and policy notifications, have no notification even if a rebuild applies them for the first time — left as is (`docs/운영.md` §7).) | Replay: `portal-ingest` `apply.Rebuilder` calls only the projectors — the deriver (`app.ingest.JdbcNotificationDeriver`) is given only to the applier (`IngestApplier`), and ArchUnit `theRebuilderReachesNoDerivation` checks zero transitive dependencies of the rebuild (`NoopNotificationDeriver` was moved to the test fixtures). Screen wording: Phase 4 `messages.ko.json` |
| 13 | **Display of expected carried-over receivables** (v1.6.1 — Phase 3 acceptance review Q5): the provisional statement's `carriedReceivable` (e.g. 720,000 in A-1 202702) is not put into the payout-run columns (Phase 2 plan ②), so it is not visible on screen. Showing it needs a storage column — a V7 candidate (v1.8: V6 was used by Phase 5's `simulation_log`). (v1.9: V7 was used by Phase 6 notifications and inquiries — V8 candidate.) | None — V8 candidate |
| 14 | **Token renewal convention of the webview host** (v1.7 — portal Phase 4): how the host puts the token in again before it expires, and how "다시 시도" (try again) on the 401 screen reaches the host (currently a reload — assuming the host puts the fragment in again). `location.reload()` cannot revive the token on its own (the boot has removed the fragment) — the host must intercept the reload and reopen with `#token=` attached (v1.7.1 — Phase 4 acceptance review Q9; the default assumption stays until Phase 8 — this button disappears with the switch to PKCE). To be agreed with the adopting GA's app. (v1.11 — **closed**, portal Phase 8 ③: the browser renews by itself with PKCE login and refresh rotation (§9 browser login). The webview host's convention is settled as the default assumption — a session whose token came from the fragment discards it on 401, and the host intercepts "다시 시도" (reload) and reopens with `#token=` attached. On an origin with a registered login configuration, a "로그인" (log in) button appears instead of this button.) | `web/src/boot/token.ts` (token source `fragment` · `pkce`) · `web/src/auth/oidc.ts` · `web/src/ui/States.tsx` (`ReloginScreen`) — no marker (closed) |

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

The two lines of one calculation arrive as `계산·계산·전기·전기` (calc · calc · post · post) (the engine's post-save hook posts after all line saves are done) — the portal links them by `calcId` and does not rely on order (Phase 2 directive D-6).

**A-2. Deferral-scheme policy** — contracted 2027-02-01 (4-year curve example): `CommissionCalculated` (first-year payout portion) + `DeferralScheduled`×N (reason CURVE) → 48 months scheduled on the timeline; the monthly due-date batch's `DeferralStatusChanged(RELEASED)` → that month's `CommissionCalculated(DEFERRED)` → reflected in the statement. `HELD` in months where the policy is not maintained.

**A-3. Rebase on reduction** — `LimitLedgerRebased`(newLimit < accum) → "Rebased" badge on the limit card, remaining shown as 0; the event handling the excess follows the engine's policy parameters (the portal just follows the events).

(All amounts, rates and tax amounts are example values.)

## Appendix B. Demo tenant data specification (`portal-demo`)

> (v1.11 — portal Phase 8 ⓪, plan Q1 · P-3 · P-4, DD-21) **The demo stream is also made by the engine.** A generator inside the portal would end up calculating commissions, tax and limits (rule 1), so there is none. The portal takes in, byte for byte, the outbox that the engine's `DemoContractTest` (`DemoScenario` — test code and test fixtures only) produced through its own pipeline: `contracts/shared/ga-commission-engine/samples/demo/` (`demo_ga.jsonl` 17,856 events, `statements.json`, `simulations.json`, `cases.json`, `UPSTREAM` engine `9963809`). `portal-demo` is only seeding, registration and the file feed server (zero calculation).

- 1 tenant ("데모GA", "Demo GA"), 30 agents (1 headquarters · 2 branches · 6 teams — five per team, grades alternating SR · JR), 432 policies (420 base + 12 required cases). Contract dates **2026-03 to 2027-06** (the rates of the engine's standard rules are effective from 2026-01-01, so contracts from 2025 produce nothing — P-3) → covering both sides of the 2026-06-30/07-01 boundary (limit not applied/applied) and the 2026-12-31/2027-01-01 boundary (deferral not applied/applied). The stream window is **2026-03 to 2028-02** (24 months — close and payout 202603 to 202801, 202802 a provisional statement): the first deferred portion of a curve deferral falls due 12 months after the contract, and the engine creates no deferral schedule for DEFER_AFTER_FY (contract `limitCutPolicy` description), so for HELD to occur within the window there must be a 202801 close.
- Events: monthly installment receipts, incentives (flat — one in five policies), surrenders (about 5%/year), reductions (about 2%/year), close (5th of the following month), payout (10th of the following month) — deterministic rules (engine test code).
- Cases that must be included (`cases.json` of the engine points to them by seq — if any one is missing, the engine test fails): at least 1 each of limit 80/95/100%, 1 each of cut (CUT) and deferral (DEFER_AFTER_FY) (the engine's standard rules have only DEFER_AFTER_FY — the demo rules split the limit rule by contract-date period: 2026-07 to 09 CUT, from October DEFER_AFTER_FY, P-4), 1 rebase, 1 HELD deferral (injected engine retention decision — P-4), 1 clawback receivable carry-over, 1 reversal/re-entry pair, 1 post-close adjustment (ADJUSTMENT), the four boundaries.
- When the portal is started without the engine, the `portal-demo` file feed server serves the `/internal/v1/events` contract as is from this stream, and simulations and statements are answered only from the samples of the same bundle (500 outside the samples — the same convention as the harness stubs).
- The business dates are those of the stream (2026-03 to 2028-02). "This month" on the screens and the reconciliation window use the real date (`Clock`), so on any other date this month on the home screen is empty (P-20 — §14.2).
---

## Appendix C. Notes from a portfolio perspective

What to emphasize in the README: ① why it focuses on "showing" rather than recalculating money, and where that boundary lies (only sums and ratios) ② the three states Estimated/Confirmed/Paid and DRIFT "확인 중" (under review) — a case of turning trust into a UI convention ③ a read model that can be rebuilt without a broker, via the outbox pull feed ④ three-layer multitenant isolation (context, architecture tests, RLS) ⑤ contract version management between two repositories. As in the engine repository, record the AI collaboration methodology (design document → Phase directive → evidence-based acceptance) in one section.

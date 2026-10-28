> 🌐 [한국어](README.md) · **English** — translation of `README.md` at commit `c3ac609`. The Korean original is canonical; where the two differ, the Korean text prevails.
>
> English documents: [design document](docs/en/design.md) · [operations](docs/en/operations.md) · [authorization matrix](docs/en/authz-matrix.md) · [real-engine E2E](e2e/engine-feed/README.en.md)

# ga-agent-portal

A read-only multi-tenant portal where agents of a GA check for themselves **"how much do I get this month · how much limit is left on this policy · how much will come out each month from now on · how much do I pay back if it is surrendered"**. Commission calculation, payout, clawback and settlement are done by the separate repository `ga-commission-engine`; the portal projects the engine's event stream from the agent's point of view and **only displays** it — it does not recalculate amounts (only integer sums and display-only ratios), attaches an estimated, confirmed or paid status to every amount, and its read model can be rebuilt from event seq 0 at any time. Shared value objects, the tenant-isolation foundation and the event Envelope core come from the platform artifacts published by `ga-disclosure` (`com.ga.platform` 0.2.0).

> **Disclaimer** — This is a learning and portfolio repository. Regulatory content (the 1200% rule, the deferral scheme, etc.) is an illustrative summary based on public materials; the actual standards are the original texts and authoritative interpretations of the Financial Services Commission (금융위원회) and the Financial Supervisory Service (금융감독원). All rates, amounts and thresholds are fictional example values. This system does not assert "compliance with legal requirements".

## Status

Phase 0 (skeleton) accepted (2026-10-16) — [plan](docs/phase-00-계획.md) · [approval](docs/phase-00-계획승인.md) · [report](docs/phase-00-보고서.md) · [acceptance review](docs/phase-00-수용심사.md). Tags `phase-0` (report, 1st edition) · `phase-0.1` (acceptance conditions applied).

Phase 1 (ingest) accepted (2026-10-17) — [directive](docs/phase-01-지시문.md) · [plan](docs/phase-01-계획.md) · [approval](docs/phase-01-계획승인.md) · [report](docs/phase-01-보고서.md) · [acceptance review](docs/phase-01-수용심사.md). Tag `phase-1`.

Phase 2 (projection) accepted (2026-10-17) — [directive](docs/phase-02-지시문.md) · [plan](docs/phase-02-계획.md) · [approval](docs/phase-02-계획승인.md) · [report](docs/phase-02-보고서.md) · [acceptance review](docs/phase-02-수용심사.md). Tag `phase-2`.

Phase 3 (query API) accepted (2026-10-17) — [directive](docs/phase-03-지시문.md) · [plan](docs/phase-03-계획.md) · [approval](docs/phase-03-계획승인.md) · [report](docs/phase-03-보고서.md) · [acceptance review](docs/phase-03-수용심사.md). Tag `phase-3`.

Phase 4 (screens, first round) accepted (2026-10-22, condition A1 → `phase-4.1`) — [directive](docs/phase-04-지시문.md) · [plan](docs/phase-04-계획.md) · [approval](docs/phase-04-계획승인.md) · [report](docs/phase-04-보고서.md) · [acceptance review](docs/phase-04-수용심사.md). Tags `phase-4` · `phase-4.1`.

Phase 5 (deferral timeline and simulation) accepted (2026-10-23, condition B1 → `phase-5.1`) — [directive](docs/phase-05-지시문.md) · [plan](docs/phase-05-계획.md) · [report](docs/phase-05-보고서.md) · [acceptance review](docs/phase-05-수용심사.md). Tags `phase-5` · `phase-5.1`.

Phase 6 (notification derivation and inquiries) accepted (2026-10-24, no conditions) — [directive](docs/phase-06-지시문.md) · [plan](docs/phase-06-계획.md) · [approval](docs/phase-06-계획승인.md) · [report](docs/phase-06-보고서.md) · [acceptance review](docs/phase-06-수용심사.md). Tag `phase-6`.

Phase 7 (reconciliation, automatic DRIFT recovery, observability) accepted (2026-10-28, no conditions) — [directive](docs/phase-07-지시문.md) · [plan](docs/phase-07-계획.md) · [approval](docs/phase-07-계획승인.md) · [report](docs/phase-07-보고서.md) · [acceptance review](docs/phase-07-수용심사.md). Tag `phase-7`. Closed table of metrics, logs and spans: [`docs/지표.md`](docs/지표.md).

Phase 8 (demo tenant and integrated run — the last Phase) reported — [directive](docs/phase-08-지시문.md) · [plan](docs/phase-08-계획.md) · [approval](docs/phase-08-계획승인.md) · [report](docs/phase-08-보고서.md). Tag `phase-8`; merge and `v1.0` after the acceptance review reply.

Canonical design document [`docs/설계서.md`](docs/en/design.md) (v1.11), decision record [`docs/DECISIONS.md`](docs/DECISIONS.md) (index at the top).

## Running

Requirements: a JDK (Gradle downloads toolchain 25), Docker (integration tests — without it the integration tests **fail**). Gradle also downloads Node 24 and pnpm for the screens (`web/`) — do not install them separately.

```bash
./gradlew build                      # 단위·아키텍처·통합 테스트 + 빌드 검사(SNAPSHOT 0, 플랫폼 SHA 고정, 테스트 입력 선언)
./gradlew printPlatformArtifacts     # 해석된 플랫폼 0.2.0 파일 13개의 경로·SHA-256(인계 §6 표와 대조)
./gradlew :web:e2e                   # 화면 E2E(Playwright 세 프로젝트, Docker 필요 — 브라우저는 :web:e2eInstall)
```

(The comments, in order: unit, architecture and integration tests + build checks — zero SNAPSHOTs, pinned platform SHAs, declared test inputs; paths and SHA-256 of the 13 resolved platform 0.2.0 files, to compare with the handoff §6 table; screen E2E — three Playwright projects, Docker required, browsers via `:web:e2eInstall`.)

The platform artifacts (`com.ga.platform:*:0.2.0`) are resolved

- **locally**: only from `mavenLocal()` — install them from ga-disclosure with `./gradlew publishToMavenLocal` (tag `platform-v0.2.0`).
- **in CI**: only from GitHub Packages (`hjryoo-ai/ga-disclosure`). Credentials are the repository secrets `GHP_USER` · `GHP_READ_TOKEN` (PAT classic, `read:packages`).

Either way, the build fails if the SHA-256 of a downloaded file differs from `gradle/verification-metadata.xml`.

## Demo (Phase 8 — `docker compose`)

Requirements: Docker only (no credentials — the image build makes the platform from the source of the public `ga-disclosure` tag `platform-v0.2.0`).

```bash
docker compose -f docker/compose.yaml --profile demo up --build
```

- Screens at <http://localhost:8080> → "로그인" (log in) → log in as a demo agent in the Keycloak window (the first sync takes about 2–3 minutes — 138–149 s measured in CI — it is done when the chip at the top reads "기준 …" (as of …)).
- Demo agents: `a-2001` to `a-2030` (A-2001 to A-2030 of the engine's demo stream, fictional). Demo password: `portal-demo-2026` — a fictional value, used only in the demo realm (`docker/keycloak/realm.json` — hashes only).
- Traces at <http://localhost:16686> (Jaeger). All three are bound to `127.0.0.1` only. The management port (metrics `/actuator/prometheus`) and the DB are not exposed to the host.
- Secrets (DB roles, the engine service token, the policyKey secret, the Keycloak administrator) are generated as files in the `secrets` volume on first start — they are not in the repository, `.env` or the images. To wipe and start fresh: `docker compose -f docker/compose.yaml --profile demo down -v`.
- Configuration and operator commands: design document §3.2 (v1.11) · [`docs/운영.md`](docs/en/operations.md) §9.
- The business dates of the demo data are 2026-03 to 2028-02 (the engine's demo stream — design document appendix B). "This month" on the home screen follows the real date, so it is empty on any date outside that range — pick past months on the statements screen.
- Installment timeline: the first installments of the demo policies fall due in 2028-01 — pick a period that reaches that month (24 months as of now).

**Values to enter on the simulation screen** (the demo engine stand-in answers only the response samples the engine produced in advance — any other value gives the "엔진 응답 없음" (no engine response) degraded screen. The screen does not pre-fill values):

- New policy: product key `WHOLE-LIFE-20Y`, monthly premium `100000`, contract date `2028-02-15` — the same for all 30 agents.
- Surrender: the policy in the table below (pick it by its last four digits in the limits card or the simulation screen's policy list), the reference date and the elapsed installment count.

<details><summary>Surrender samples per agent (30)</summary>

| Agent | Policy (last 4 digits) | Reference date | Elapsed installments |
|---|---|---|---|
| `a-2001` | …0002 | 2028-02-15 | 7 |
| `a-2002` | …0015 | 2028-02-15 | 7 |
| `a-2003` | …0029 | 2028-02-15 | 7 |
| `a-2004` | …0043 | 2028-02-15 | 7 |
| `a-2005` | …0057 | 2028-02-15 | 7 |
| `a-2006` | …0071 | 2028-02-15 | 7 |
| `a-2007` | …0085 | 2028-02-15 | 7 |
| `a-2008` | …0099 | 2028-02-15 | 7 |
| `a-2009` | …0113 | 2028-02-15 | 7 |
| `a-2010` | …0128 | 2028-02-15 | 7 |
| `a-2011` | …0141 | 2028-02-15 | 7 |
| `a-2012` | …0155 | 2028-02-15 | 7 |
| `a-2013` | …0169 | 2028-02-15 | 7 |
| `a-2014` | …0184 | 2028-02-15 | 7 |
| `a-2015` | …0197 | 2028-02-15 | 7 |
| `a-2016` | …0211 | 2028-02-15 | 7 |
| `a-2017` | …0225 | 2028-02-15 | 7 |
| `a-2018` | …0239 | 2028-02-15 | 7 |
| `a-2019` | …0253 | 2028-02-15 | 7 |
| `a-2020` | …0267 | 2028-02-15 | 7 |
| `a-2021` | …0281 | 2028-02-15 | 7 |
| `a-2022` | …0295 | 2028-02-15 | 7 |
| `a-2023` | …0309 | 2028-02-15 | 7 |
| `a-2024` | …0323 | 2028-02-15 | 7 |
| `a-2025` | …0337 | 2028-02-15 | 7 |
| `a-2026` | …0353 | 2028-02-15 | 7 |
| `a-2027` | …0366 | 2028-02-15 | 7 |
| `a-2028` | …0379 | 2028-02-15 | 7 |
| `a-2029` | …0393 | 2028-02-15 | 7 |
| `a-2030` | …0407 | 2028-02-15 | 7 |

</details>

## Ingest operations (Phase 1)

Polling that reads the engine feed per tenant runs only when `PORTAL_INGEST_ENABLED=true`. The tenant list comes via the dedicated role `portal_directory` (`PORTAL_DIRECTORY_USER` · `PORTAL_DIRECTORY_PASSWORD`); service tokens are per-tenant files `$PORTAL_ENGINE_TOKEN_DIR/<TENANT_ID>` (readable by the owner only — values are never written to the repository or logs). Operator commands for handling a stalled tenant:

```bash
java -jar portal-app.jar --spring.profiles.active=cli ingest status [--tenant=GA01]
java -jar portal-app.jar --spring.profiles.active=cli ingest resume --tenant=GA01 --from-seq=120
java -jar portal-app.jar --spring.profiles.active=cli ingest pause --tenant=GA01
java -jar portal-app.jar --spring.profiles.active=cli ingest rebuild --tenant=GA01   # Phase 2 — 설계서 §6.7
java -jar portal-app.jar --spring.profiles.active=cli inquiry list --tenant=GA01 [--status=OPEN]   # Phase 6 — docs/운영.md §7
java -jar portal-app.jar --spring.profiles.active=cli inquiry answer --tenant=GA01 --id=<uuid> --answer-file=<path>   # 답변은 파일로만
java -jar portal-app.jar --spring.profiles.active=cli inquiry close --tenant=GA01 --id=<uuid>
java -jar portal-app.jar --spring.profiles.active=cli recon run --tenant=GA01 [--ym=202608,202702]   # Phase 7 — docs/운영.md §8
java -jar portal-app.jar --spring.profiles.active=cli status [--tenant=GA01 | --all]                 # 값 없는 한 줄 — 운영 알림 채널 v1
```

(The comments: `rebuild` — design document §6.7; `inquiry` — operations §7, answers only via a file; `recon run` — operations §8; `status` — one line without values, the operational alert channel v1.)

Every invocation is recorded in `audit_access` (rejections too). `--from-seq` accepts only values at or below the last applied seq — skipping ahead is possible only via rebuild. **Authorization (Phase 3)**: the actor is the OS user who ran the command, and `cli:<OS 사용자>` (OS user) must be registered as `OPERATOR` in that tenant's `identity_link` (registration SQL in `docs/운영.md` §3). `--actor` has been removed (exit code 64). A rejection prints `T not authorized` + exit code 2.

`rebuild` (Phase 2) rebuilds that tenant's views from feed seq 1 in **a single transaction** — if it fails, nothing changes and the cursor becomes `FAILED(REBUILD_FAILED: …)` (exit code 1). Queries keep seeing the old rows during a rebuild. It does not start if the feed head exceeds `portal.ingest.rebuild.max-events` (default 50,000).

## Projection (Phase 2)

Projects 13 engine event types into 9 view tables (design document §4.2 table · §5). Amounts hold the engine's values as is, and the portal only does integer sums. Golden: `portal-projection/src/test/resources/golden-streams/` (streams; source hashes in its README) and `golden-views/` (expected rows — verified by hand). If the totals of a close or payout event differ from the portal's sum, that is not a poison event but a `DRIFT` for that month (metric `projection_drift_total`).

Free-form strings from the engine, such as `reason` in `earnings_line.basis`, are not agent or customer values and therefore not subject to the plaintext scan (they are engine rule descriptions) — they are stored, but not carried into logs or error messages.

## Query API (Phase 3)

The agent's own queries `/api/v1/me/**` (contract `contracts/api/v1/portal-api.openapi.yaml`, authorization table `docs/authz-matrix.md`). Authentication is an RS256 access token from the tenant's IdP — the tenant is determined only by the token issuer (`iss`), and the agent and role only by `identity_link` (no agentId in the path or query). Rejections are byte-identical (one 401, one 404, one 400 for query format). Policy numbers are not in responses — only `policyMasked` · `policyKey` (HMAC with a deployment secret). Operational registration (issuer, agent link, operator, `policyKey` secret) is in `docs/운영.md`. Required for deployment: `PORTAL_ISSUER_USER` · `PORTAL_ISSUER_PASSWORD` (dedicated role `portal_issuer`), `PORTAL_POLICY_KEY_SECRET_FILE`.

Real-engine E2E (outside the regular build, run by hand): `ENGINE_REPO=<엔진 commission-system 경로> e2e/engine-feed/run.sh` (path to the engine's commission-system) — engine local stack → portal (ingest on) → 9 view tables = golden `a1`, one query API call passes the contract → `e2e/engine-feed/RESULT.md`. Procedure and secret handling in `e2e/engine-feed/README.md`.

## Screens (Phase 4 · 5)

`web/` — a React 19 + TypeScript + Vite mobile web app. The build output goes inside the `portal-app` boot jar and is served from the **same origin** as the API (no CORS, CSP `'self'` only). Screens: home `/`, monthly statements `/statements`, line detail `/lines/{calcId}`, limits `/limits`; Phase 5 deferral timeline `/deferrals` and simulation `/simulations` (only the server calls the engine — results are labelled "설계사 참고용 · 비확정 · 고객 제시 불가" (for the agent's reference · not final · not to be shown to customers)); Phase 6 notification inbox `/notifications`, inquiries `/inquiries` · `/inquiries/new` · `/inquiries/{id}` (notifications are in-app only — no push, KakaoTalk notification messages (알림톡) or mail; operators answer via the CLI). The webview host puts the token in as `/#token=<JWT>` and the screens keep it only in memory (after a reload it must be put in again). Conventions are in `CLAUDE.md` "화면 규약" (screen conventions); development commands are in [`web/README.md`](web/README.md).

To see the screens locally (Docker required):

```bash
./gradlew :web:e2eServe              # PostgreSQL·발급자·피드 스텁(GA01C~GA04C, 허구 스트림 전부)·엔진 시뮬레이션 스텁(엔진 샘플 사본) + 포털 — Ctrl-C로 정리
```

(Starts PostgreSQL, an issuer, a feed stub (GA01C to GA04C, all fictional streams), an engine simulation stub (a copy of the engine samples) and the portal — Ctrl-C cleans up.)

The login URLs (`/#token=…` containing a token) are not printed to the terminal; they are written only to `web/build/e2e/.state/login-urls.txt` (directory 700, file 600; it is under `build/`, so it is not committed). Open a URL from that file in a browser. The tokens are made by the harness's test issuer, so they cannot be used after 3 hours or once the harness goes down.

## Portfolio perspective (design document appendix C)

1. **No recalculating money — the boundary of "only displaying".** The engine calculates commissions, limits, tax and clawbacks; the portal sums engine facts as integers and makes only display-only ratios. `double`, multiplication and division are blocked by bytecode and SQL scans and ArchUnit (the allowlist is closed FQNs), and amounts on the screens are an opaque type, so arithmetic on them is a type error. Questions that need a calculation (simulation, statement reconciliation) call the engine.
2. **Trust as a UI convention.** Every amount carries an estimated, confirmed or paid label (a 16-cell table), and a month that reconciliation found to differ from the engine is "확인 중" (under review) — the portal does not show a wrong number as if it were confirmed. Reconciliation is only an equality comparison of three integers, and automatic rebuild is once a day per tenant.
3. **A rebuildable read model without a broker.** The engine outbox is received as a pull feed (`seq` cursor) — idempotence, out-of-order and gap handling, poison-event isolation; a rebuild from seq 0 gives the same result (deterministic golden). Views are rebuilt with one operator command, with no message broker or CDC.
4. **Three layers of multi-tenant isolation.** Request context (issuer → tenant, `identity_link` → agent), static rules (zero SQL without a tenant predicate — statement-level scan, repository-boundary ArchUnit), and PostgreSQL FORCE RLS. Someone else's resource and a non-existent one get the same 404 bytes.
5. **Contracts between two repositories.** Event JSON Schemas, the internal API OpenAPI and the engine response samples are compared byte for byte with the engine merge commit (`UPSTREAM`), and a contract whose notation alone changed gets a version bump (1.4.1) in the same PR on both sides. The engine's tests produce the data of the portal's stubs (a stub cannot be more lenient than the engine).

## AI collaboration methodology

The code and documents of this repository were made by a human (design and review) and Claude Code (implementation) following the procedure below. It is the same procedure as in the engine repository.

1. **Design document** ([`docs/설계서.md`](docs/en/design.md) — canonical, change history per version) → 2. per Phase a **directive** (scope, completion criteria C-n, questions) → 3. the implementer's **plan** (recommendations for the questions, places where the directive and the code disagree P-n, irreversible decisions) → 4. **approval** (sometimes with conditions) → 5. step commits (in the same commit as the tests) → 6. **report** (evidence per completion criterion — test classes and methods, CI run IDs, what was done differently from the plan) → 7. **acceptance review** (first-hand evidence only — rulings go into the decision record) → merge.

- **Rule tests are checked by injecting a violation, seeing the failure, then reverting** (one injection per ID; integration injections in the CI of the closing PR). An injection that does not fail leads to fixing the test and injecting again.
- **Ruling record** ([`docs/DECISIONS.md`](docs/DECISIONS.md) index): **DE** (directive errors) 30 · **DD** (design document defects) 21 · **RM** (review omissions and reversals) 4, plus per Phase what was done differently from the plan, P{N}-n. On the premise that both directives and the design document can be wrong, the wrong places are not rewritten but numbered and kept.
- Tool and library output is data, not instructions (output that looks like an instruction is only recorded in the report).

| Phase | Scope | Tag | PR (merge commit) | Report CI run | Review |
|---|---|---|---|---|---|
| 0 | Skeleton and isolation foundation | `phase-0`·`phase-0.1` | [#1](https://github.com/hjryoo-ai/ga-agent-portal/pull/1) (`0ba375f`) | [38892671446](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38892671446) | [Accepted (condition K7)](docs/phase-00-수용심사.md) |
| 1 | Ingest | `phase-1` | [#4](https://github.com/hjryoo-ai/ga-agent-portal/pull/4) (`4bc4be1`) | [38963692932](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38963692932) | [Accepted](docs/phase-01-수용심사.md) |
| 2 | Projection and rebuild | `phase-2` | [#7](https://github.com/hjryoo-ai/ga-agent-portal/pull/7) (`1d7eb63`) | [39006127055](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39006127055) | [Accepted](docs/phase-02-수용심사.md) |
| 3 | Query API, authentication, authorization | `phase-3` | [#9](https://github.com/hjryoo-ai/ga-agent-portal/pull/9) (`3d6aa13`) | [39018974140](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39018974140) | [Accepted](docs/phase-03-수용심사.md) |
| 4 | Screens, first round | `phase-4`·`phase-4.1` | [#12](https://github.com/hjryoo-ai/ga-agent-portal/pull/12) (`86d6fe6`) | [39035626322](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39035626322) | [Conditionally accepted (A1)](docs/phase-04-수용심사.md) |
| 5 | Deferral timeline and simulation | `phase-5`·`phase-5.1` | [#13](https://github.com/hjryoo-ai/ga-agent-portal/pull/13) (`3a24ce6`) | [39951478495](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39951478495) | [Conditionally accepted (B1)](docs/phase-05-수용심사.md) |
| 6 | Notification derivation and inquiries | `phase-6` | [#20](https://github.com/hjryoo-ai/ga-agent-portal/pull/20) (`b87db17`) | [40094460151](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40094460151) | [Accepted](docs/phase-06-수용심사.md) |
| 7 | Reconciliation, automatic DRIFT recovery, observability | `phase-7` | [#28](https://github.com/hjryoo-ai/ga-agent-portal/pull/28) (`2ccfc44`) | [40504138779](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40504138779) | [Accepted](docs/phase-07-수용심사.md) |
| 8 | Demo, PKCE, observability stack, k6 | `phase-8` | [#35](https://github.com/hjryoo-ai/ga-agent-portal/pull/35) | Report §1 | Awaiting review |

## Modules

| Module | Role | Phase |
|---|---|---|
| `portal-domain` | Portal-specific value objects (`CalcId` · `PolicyNo` · `LedgerId` · `ScheduleId` · `CommType`) and status enums. No Spring or DB dependencies | 0 |
| `portal-projection` | Projectors (event → view) and notification derivation rules. No Spring or DB dependencies | 2·6 |
| `portal-ingest` | Event feed client, cursor, idempotent apply, rebuild | 1·2 |
| `portal-query` | Query API (`/api/v1/me/**`) and authorization | 3 |
| `portal-sim` | Engine simulation proxy, `simulation_log` | 5 |
| `portal-inbox` | Notification inbox and inquiry use cases (list, mark read, submit an inquiry, view). No Spring or DB dependencies | 6 |
| `portal-recon` | Reconciliation, DRIFT, observability metrics | 7 |
| `portal-infra` | PostgreSQL adapter, Flyway, RLS | 0 |
| `portal-app` | Spring Boot assembly, architecture tests | 0 |
| `portal-demo` | Demo engine stand-in — serves the engine's demo bundle (stream, statements, simulation samples) on three paths of the engine's internal contract (zero calculation; no product module depends on it) | 8 |
| `docker/` | compose demo (`--profile demo`), image build, secret generation, Keycloak realm, CI secret scan | 8 |
| `perf/` | k6 home bundle (30 agents concurrently) — manual workflow `perf.yml` | 8 |
| `web/` | React mobile web (same origin — inside the boot jar) | 4 |
| `contracts/` | Engine↔portal contracts (JSON Schema, OpenAPI), `shared/ga-disclosure/` copy of the shared core | 0 |

## License

MIT — [`LICENSE`](LICENSE).

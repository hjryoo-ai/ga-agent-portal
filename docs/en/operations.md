> 🌐 [한국어](../운영.md) · **English** — translation of `docs/운영.md` at commit `c3ac609`. The Korean original is canonical; where the two differ, the Korean text prevails.

# Portal operating procedures (ga-agent-portal)

> Operational registration procedures from portal Phase 3 (query API, authentication, authorization) onward. Design document §9 v1.6 is canonical; this document records its procedures. Values (issuer URL, subject, agent ID, secrets) are
> not written in this document or anywhere in the repository — `<…>` in the SQL below are placeholders. Create the registration SQL as a **file** and run it with `psql -f` (do not carry values in shell arguments or environment variables).
> Every registration and revocation is done as the schema owner `portal_migrator`, inside a transaction that sets that tenant on the session (`tenant` and `identity_link` are FORCE RLS).

## 1. Opening tenant login — issuer registration

The token's `iss` determines the tenant (design document §9). `oidc_issuer` is an exact match (including case and trailing slash) and is unique across tenants. If both are NULL, that tenant has no login.

```sql
-- register-issuer.sql  (psql -v ON_ERROR_STOP=1 -f register-issuer.sql)
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
UPDATE tenant SET oidc_issuer = '<https://idp.example/realms/ga01>', oidc_audience = '<portal-ga01>' WHERE tenant_id = '<TENANT_ID>';
COMMIT;
```

- The `issuer` in the issuer metadata (`<issuer>/.well-known/openid-configuration`) must equal this value. JWKS is fetched from that metadata (only for registered issuers — the portal never calls out to a URL chosen by the token).
- Revocation (closing login): set both to NULL. 401 from the next request on (no cache).
- The issuer table is read on every request with the dedicated role `portal_issuer` (these three columns only) — that role's credentials are `PORTAL_ISSUER_USER` · `PORTAL_ISSUER_PASSWORD` (from the secrets manager).

## 2. Agent link registration — `identity_link` (AGENT)

The agent and role come only from this row (the token's `agent_id` and `roles` claims are not used). `org_path` is the organization for portal authorization (the scope of v2 organization queries) — its source differs from the engine's `agent_profile.org_path` (display-only), and even if they differ, neither side is corrected.

```sql
-- link-agent.sql
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
VALUES ('<TENANT_ID>', '<IdP subject>', '<AGENT_ID>', 'AGENT', 'ACTIVE', '</HQ1/BR03/TM07>', now());
COMMIT;
```

- Revocation: `UPDATE identity_link SET status = 'REVOKED' WHERE tenant_id = '<TENANT_ID>' AND subject = '<IdP subject>';` — the row is not deleted (audit records point to that subject). 404 from the next request on.
- `MANAGER` is the role for v2 organization queries — currently `/api/v1/me/**` is open only to `AGENT` (`docs/authz-matrix.md`).

## 3. Operator registration — CLI authorization (OPERATOR)

The actor of operator commands (`ingest status|resume|pause|rebuild`) is **the OS user who ran the command** — subject `cli:<OS 사용자>` (OS user) must be an `OPERATOR` · `ACTIVE` link of that tenant (`--actor` has been removed — using it gives exit code 64). An operator is not an agent, so it has no `agent_id` or `org_path`.

```sql
-- register-operator.sql
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
VALUES ('<TENANT_ID>', 'cli:<os-user>', NULL, 'OPERATOR', 'ACTIVE', NULL, now());
COMMIT;
```

- Register separately for each tenant. `ingest status` without a tenant checks each tenant, shows unregistered tenants as `T not authorized`, and exits with code 2 if there is at least one.
- A rejection is recorded in that tenant's `audit_access` as `AUTHZ_DENIED` (detail `NO_LINK` · `REVOKED` · `ROLE`, target `cli:ingest <명령>` (명령 = command)).
- Calling `/api` with a token for an `OPERATOR` link gives 404 (there is no agent).
- Revocation is `REVOKED`, as in 2.

## 4. `policyKey` secret — deployment and rotation

The response's `policyKey` = the first 16 characters of HMAC-SHA256(deployment secret, `tenant_id` + `U+001F` + `policy_no`) (design document §7). The secret is the file `portal.query.policy-key-secret-file` (environment `PORTAL_POLICY_KEY_SECRET_FILE`, default `/run/secrets/portal-policy-key`) — 32 or more random bytes, readable by the owner only. The web application does not start without this file.

- **Rotation changes every `policyKey`.** The screens (Phase 4) do not store keys — keys in bookmarks and saved filters yield empty results after rotation (not an error).
- Rotation procedure: deploy the new secret file → restart instances one by one (between restarts, keys may differ per instance — if the same screen alternates between two instances, a filter may yield an empty result).
- Never write the secret value in the repository, logs or tickets.

## 5. Sync delay threshold

The threshold in seconds for `dataStatus = SYNC_DELAYED` is the tenant's `params.sync_delay_seconds` (a positive integer of 1–9 digits); if absent or malformed, the setting `portal.query.default-sync-delay-seconds` (default 300). If the cursor is not `RUNNING` (including during a rebuild), the data counts as delayed regardless of the threshold.

```sql
-- sync-delay.sql
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
UPDATE tenant SET params = params || '{"sync_delay_seconds": 600}'::jsonb WHERE tenant_id = '<TENANT_ID>';
COMMIT;
```

## 6. Simulation — rate limit, retention scenario, log retention, engine timeouts (portal Phase 5)

Design document §9 · §10 v1.8. Tenant values are in `tenant.params`; engine timeouts are application settings (plan Q10).

| Key | Location | Default | Meaning |
|---|---|---|---|
| `sim_rate_per_minute` | `tenant.params` (positive integer of 1–9 digits) | application setting `portal.sim.default-rate-per-minute` = 10 | Upper limit on simulation requests per agent in the preceding 60 seconds — above it, 429 `RATE_LIMITED` + `Retry-After` |
| `forecast_scenario_enabled` | `tenant.params` (JSON boolean) | false | Opens the retention-rate assumption (RETENTION) of the deferral timeline — when off, `scenario=RETENTION` is 400 and the screen has no toggle |
| `sim_log_retention_years` | `tenant.params` (positive integer) | 3 | Retention in years for `simulation_log` — **documentation only**: there is no deletion job (directive ②). The operator runs the SQL below by hand |
| `portal.sim.connect-timeout` · `portal.sim.read-timeout` | application setting (environment `PORTAL_SIM_CONNECT_TIMEOUT` · `PORTAL_SIM_READ_TIMEOUT`) | 2s · 10s | Connect and read timeouts of engine simulation calls — when exceeded, 503 `ENGINE_UNAVAILABLE`, no retry |

```sql
-- sim-params.sql — 리밋 20/분, 유지율 시나리오 켬
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
UPDATE tenant SET params = params || '{"sim_rate_per_minute": 20, "forecast_scenario_enabled": true}'::jsonb WHERE tenant_id = '<TENANT_ID>';
COMMIT;
```

(Comment: limit 20/minute, retention scenario on.)

Retention cleanup (per tenant, by hand — look at a `SELECT count(*)` with the same condition first, then delete). A rebuild does not delete `simulation_log` (design document §6.7):

```sql
-- sim-log-retention.sql
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
DELETE FROM simulation_log
 WHERE tenant_id = '<TENANT_ID>'
   AND created_at < now() - make_interval(years => coalesce((SELECT (params->>'sim_log_retention_years')::int FROM tenant WHERE tenant_id = '<TENANT_ID>'), 3));
COMMIT;
```

The engine service token is the same file as for ingest (`PORTAL_ENGINE_TOKEN_DIR/<TENANT_ID>`) — if the file is missing, the simulation does not call the engine and returns 503 (log reason `TOKEN_MISSING`), while queries are unaffected.

## 7. Notification inbox and inquiries — inquiry CLI, inquiry limit, notification retention (portal Phase 6)

Design document §6.5 · §9 · §10 v1.9. Notifications arise inside the ingest apply transaction (nothing for the operator to do); inquiries are submitted by agents on the screen and answered by operators via the CLI. There is no push, KakaoTalk notification message (알림톡) or mail (in-app only).

**Inquiry CLI** (`cli` profile — the same runner as the ingest commands, the same authorization: `cli:<OS 사용자>` is an OPERATOR of that tenant — §3). Exit codes: 0 success, 2 rejection, 64 usage.

| Command | What it does | Audit (`audit_access`) |
|---|---|---|
| `inquiry list --tenant=T [--status=OPEN\|ANSWERED\|CLOSED\|ALL]` | One tab-separated line each: id · agent · calcId (`-` if none) · category · status · submission time · **body** (newlines, tabs and backslashes as `\n` · `\t` · `\\`; other control and format characters (bidirectional etc.) and separator characters as `\uXXXX` — so that text written by an agent cannot manipulate the terminal). In submission order | `INQUIRY_LIST` (target `inquiry:<상태>` (상태 = status)) |
| `inquiry answer --tenant=T --id=<uuid> --answer-file=<path>` | `OPEN → ANSWERED`, answered by = `cli:<OS 사용자>`, and in the same transaction the notification `INQUIRY_ANSWERED` to the agent. Output is one line, `inquiry <id> ANSWERED` (the answer is not echoed). 2 if not `OPEN` | `INQUIRY_ANSWER` / `INQUIRY_ANSWER_REJECTED` (target `inquiry:<id>`) |
| `inquiry close --tenant=T --id=<uuid>` | `OPEN` · `ANSWERED → CLOSED` (closed is final — it is not reopened and the answer is not changed). 2 if already closed | `INQUIRY_CLOSE` / `INQUIRY_CLOSE_REJECTED` |

- **Answers only via a file.** There is no argument such as `--answer` (64) — the command line and environment variables remain in shell history and the process list. File: UTF-8 (invalid bytes are rejected), at most 16 KiB, 1–4,000 characters (code points) excluding one trailing newline, not whitespace only. Otherwise 64, and the message does not contain the file's content. The operator deletes the file when done (the portal only reads it).
- The body appears only in the terminal of `inquiry list` and on the author's own inquiry detail screen. Do not move `list` output into files, tickets or chat. Audit `detail`, logs and metrics contain no body.

```sh
# 예: 답변 파일을 쓰고(편집기로 — 셸 인라인 금지), 답하고, 지운다
java -jar portal-app.jar --spring.profiles.active=cli inquiry list --tenant=<TENANT_ID> --status=OPEN
java -jar portal-app.jar --spring.profiles.active=cli inquiry answer --tenant=<TENANT_ID> --id=<INQUIRY_ID> --answer-file=/secure/answers/<INQUIRY_ID>.txt
java -jar portal-app.jar --spring.profiles.active=cli inquiry close --tenant=<TENANT_ID> --id=<INQUIRY_ID>
```

(Comment: example — write the answer file (with an editor — no shell inline), answer, then delete it.)

**Inquiry limit**: if an agent's inquiry rows in the preceding 24 hours number ≥ N, 429 `RATE_LIMITED` + `Retry-After` (seconds). N is `tenant.params.inquiry_rate_per_day` (positive integer of 1–9 digits); if absent or malformed, the application setting `portal.inbox.default-inquiries-per-day` = 20. Metric `api_rate_limited_total{tenant, route="inquiries"}`.

```sql
-- inquiry-rate.sql — 문의 한도 10/일
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
UPDATE tenant SET params = params || '{"inquiry_rate_per_day": 10}'::jsonb WHERE tenant_id = '<TENANT_ID>';
COMMIT;
```

(Comment: inquiry limit 10/day.)

**Notification retention 1 year** (example value — design document §9): there is no deletion job. By `created_at`, regardless of read status, per tenant, by hand (look at a `SELECT count(*)` with the same condition first, then delete). The retention period for inquiries (`inquiry`) is not set in the design document — this Phase has no inquiry cleanup SQL (a next-Phase question in the report).

```sql
-- notification-retention.sql
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
DELETE FROM notification WHERE tenant_id = '<TENANT_ID>' AND created_at < now() - interval '1 year';
COMMIT;
```

**Rebuild and notifications**: a rebuild neither creates nor deletes notifications (`notification` and `inquiry` stay as they are). **After a rebuild, no notifications arise for events past the cursor** — a rebuild replays up to the feed head, so when rebuilding from `FAILED` or when the head is ahead of the cursor, the events in between are applied for the first time by the rebuild, and there are no notifications for those facts (limit warnings, confirmations, payouts, etc.) (plan Q25). If the agent needs to be told, the operator informs them separately. (Portal Phase 7: only the confirmation and payout notifications of a **month** that was blocked by DRIFT are inserted, with the same key, by re-reconciliation `REPAIRED` — §8, design document §6.5. Ledger, deferral and policy notifications are still absent.)

## 8. Reconciliation, automatic recovery, operational alert channel v1 (portal Phase 7)

Design document §3.4 · §6.6 · §6.7 · §10 v1.10; the closed table of metrics, logs and spans is `docs/지표.md`. Reconciliation is run once a day per tenant by a scheduler inside the application (the first 1-minute tick after KST `recon_hour_kst` — when there is no scheduled round for that day yet). Operators can invoke the same round via the CLI. **Operational alert channel v1 is metrics + the `status` CLI** — there is no email or webhook (v2).

**CLI** (`cli` profile — authorization as in §3: `cli:<OS 사용자>` is an OPERATOR of that tenant). Output is only counts, states, times and ages (no agent, month, amount or `calcId`).

| Command | What it does | Exit code | Audit |
|---|---|---|---|
| `recon run --tenant=T [--ym=YYYYMM,…]` | One reconciliation round (the same entry point as the scheduler). Without `--ym`, the window {current month, previous month} (KST); with it, those months (comma-separated, no duplicates, at most 6). One line: `T recon <결과> checked= matched= drift_new= drift_unresolved= inconclusive= engine_calls= [rebuild=]` (`<결과>` = result) | 0 round finished (`OK` · `DRIFT` · `REPAIRED`), 1 `FAILED` (automatic rebuild failed), 2 rejection or another round in progress, 64 usage | `RECON_RUN` (target `recon:T`). Automatic rebuild is `INGEST_REBUILD*` (actor `recon:T`) |
| `status --tenant=T` · `status --all` | One line per tenant: cursor state · `last_seq` · `lag_seq` (engine head − `last_seq`, `-` if the head cannot be read) · `caught_up_at` · `lag_s` · `drift_unresolved` · `drift_oldest_days` (age of the oldest unresolved DRIFT — **the month is not printed**) · `last_run` (time/result) · `last_rebuild` · `inquiries_open` · `inquiry_oldest_hours`. `--all` covers the whole tenant list — unregistered tenants are `T not authorized` | 0; 2 if any rejection; 64 | `STATUS` (target `cli:status`) per tenant |

```sh
java -jar portal-app.jar --spring.profiles.active=cli status --all
java -jar portal-app.jar --spring.profiles.active=cli recon run --tenant=<TENANT_ID> --ym=<YYYYMM>
```

**What to look at and what to do**

| Signal | Meaning | Action |
|---|---|---|
| `recon_unresolved_drift{tenant}` > 0 for a long time (`drift_oldest_days` ≥ 1 in `status`) | Even after a rebuild, portal sum ≠ engine statement — a DRIFT that has already been tried is not rebuilt again (an inconsistency in the stream itself; a correction on the engine side is needed) | Ask the engine settlement team whether that tenant and month will be corrected. If the engine issues a correction event, event reconciliation and the next round resolve it. Meanwhile the screen shows "확인 중" (under review) |
| `recon_runs_total{result="FAILED"}` · `cursor=FAILED` (`REBUILD_FAILED`) in `status` | Automatic rebuild failed — the cursor has stopped | Find the cause with `ingest status` (reason code, seq) → fix the cause → `ingest rebuild --tenant=T` (§6.7) |
| `recon_inconclusive_total{reason="BUDGET"}` rising | The round budget (`recon_max_calls` · `recon_max_seconds`) is smaller than the number of targets | Raise the limits in the tenant's `params` (SQL below). Look at the call interval `recon_call_interval_ms` and the engine load together |
| `recon_inconclusive_total{reason="LAGGING"}` rising | The round ran while the portal was behind (cursor not `RUNNING`, caught up long ago, engine head ahead) — not a DRIFT | Start with `ingest_lag_seq` · `ingest_state`. If the feed is healthy, the next round will check |
| `recon_inconclusive_total{reason="ENGINE_UNAVAILABLE"\|"ENGINE_CONTRACT"}` | The engine could not be reached, or its answer was outside the contract — zero retries | Check the engine status, the token file (§1) and the contract version. The next round checks again |
| `recon_inconclusive_total{reason="ESTIMATE_DIFF"}` | An open month's (ESTIMATE) clawback offset or corrections differ from the engine's estimate — no DRIFT is raised (design document §6.6 Q1 ⓑ′) | A round after confirmation (CONFIRMED) checks again. If it repeats, check the base seq of the engine's estimate |
| `inquiry_open_total` · `inquiry_open_oldest_age_seconds` | Unanswered inquiries (updated every minute by the reconciliation tick) | §7 `inquiry list --status=OPEN` → answer |

**Tenant settings** (`tenant.params`; if absent or malformed, the application defaults `portal.recon.defaults.*`): `recon_hour_kst` (0–23, default 4), `auto_rebuild_enabled` (default true), `recon_caught_up_within_seconds` (default 900), `recon_call_interval_ms` (default 200), `recon_max_calls` (default 500), `recon_max_seconds` (default 900).

```sql
-- recon-params.sql — 예: 상한을 올리고 자동 재구축을 끈다
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
UPDATE tenant SET params = params || '{"recon_max_calls": 2000, "auto_rebuild_enabled": false}'::jsonb WHERE tenant_id = '<TENANT_ID>';
COMMIT;
```

(Comment: example — raise the limit and turn automatic rebuild off.)

**Connections and threads** (plan Q9 — P7-4 · P7-5): a reconciliation round holds one connection of the outer transaction that holds the round lock **for the whole round** (at most `recon_max_seconds` + rebuild), and saving and rebuilding use one more connection in a new transaction — at most 2 per round. An ingest tick uses 1 while applying (a rebuild's txB uses 1 inside that tenant's lock), and there are two scheduler threads (`spring.task.scheduling.pool.size: 2` — the ingest tick and the reconciliation tick do not wait for each other). Therefore the HikariCP maximum pool (default 10) must be at least **2 for reconciliation + 1 for ingest + the number of concurrent query requests** — keep the peak access on the day after close (design document §10) and the reconciliation time (`recon_hour_kst`) from overlapping. With two instances, the round lock lets only one round run per tenant (the other instance's tick skips that tenant).

**Rebuild once a day (KST) per tenant**: even if an operator ran `ingest rebuild` during the day, that day's automatic rebuild condition (zero rebuilds today) looks only at the reconciliation round record (`recon_run.rebuilt_at`) — an operator rebuild is not a round record, so it does not prevent that night's automatic rebuild.

**Operator rebuild and DRIFT marks**: `ingest rebuild` remakes `earnings_month` from the events, but inside the transaction where the replay ends (txB), the **end-of-rebuild invariant** reads the reconciliation table (`recon_result`) and restores `data_status` to `DRIFT` for (agent, month) pairs that have an unresolved DRIFT (design document §6.7 v1.11, portal Phase 8 ① — closes Phase 7 report F11). Even after an operator rebuild, that month shows "확인 중" (under review) on the screen — there is no need to call `recon run` separately. "Unresolved" follows the same rule as reconciliation (there is no `REPAIRED` row for the same month after that DRIFT row — including at the same timestamp). A rebuild does not write the reconciliation table.

## 9. compose demo — startup, secrets, Keycloak, observability (portal Phase 8)

The demo configuration in design document §3.2 (v1.11) is canonical. The demo is the portal image with the production settings as they are; only the compose values differ — `docker/postgres/seed-demo.sql` performs the registrations of §1–§3 above as they are (tenant `DEMO`, 30 agents, operator `cli:portal`).

**Startup and shutdown**

```sh
docker compose -f docker/compose.yaml --profile demo up --build -d   # 첫 기동: 이미지 빌드 + 첫 동기화(약 2~3분)
docker compose -f docker/compose.yaml --profile demo ps -a           # seed는 exited(0), 나머지 running/healthy
docker compose -f docker/compose.yaml --profile demo down            # 볼륨(비밀·DB)은 남는다 — 다시 띄우면 같은 상태
docker compose -f docker/compose.yaml --profile demo down -v         # 전부 지운다(비밀·DB·Keycloak 세션) — 다음 기동은 처음부터
```

(Comments, in order: first start — image build + first sync (about 2–3 minutes); seed is exited(0), the rest running/healthy; volumes (secrets, DB) remain — starting again gives the same state; deletes everything (secrets, DB, Keycloak sessions) — the next start is from scratch.)

- Every service is only in the profile `demo` — `docker compose up` without a profile starts nothing.
- The only ports opened on the host are 8080 (screens and API), 8180 (Keycloak) and 16686 (Jaeger UI) on `127.0.0.1`. The DB, the management port (9090) and the demo engine exist only inside the compose network — the compose E2E checks that 9090 is closed on the host.
- First sync done: the chip at the top of the screen becomes "기준 …" (as of …). Progress is checked with the operator CLI (below).

**Secrets** (plan P-15): the service `secrets` creates them in the volume `secrets` on first start (`docker/secrets/generate.sh` — the file list and owning uids are in its header comment). The values are nowhere in the repository, `.env`, the images, environment variables, logs or `docker compose config` output — CI `compose-e2e` sweeps all of them with `docker/ci/scan-secrets.sh` (the images as `docker save` bytes). To change secrets, `down -v` (they are paired with the DB, so they are not changed separately). When a value must be read (e.g. the Keycloak admin console), do not print it on screen; copy it to a file:

```sh
docker compose -f docker/compose.yaml --profile demo cp keycloak:/run/secrets/all/keycloak/admin ./kc-admin && chmod 600 ./kc-admin   # 쓰고 지운다
```

(Comment: use it, then delete it.)

**Keycloak**: the realm `demo` is imported at startup from `docker/keycloak/realm.json` (the output of the generator script `generate-realm.py` — passwords as PBKDF2 hashes only). The demo password is the one fictional value in the README. To change the realm, fix the script and regenerate it (`python3 docker/keycloak/generate-realm.py`); `DemoComposeFilesTest` checks the README password against the 30 hashes. The client `portal-web` allows only Authorization Code + PKCE (S256); `k6` uses Direct Access Grants (for load measurement and E2E preparation only — it is never placed outside the demo realm). The issuer string is the same for the browser and the portal (`http://localhost:8180/realms/demo` — inside the portal container, `idp-relay` connects it to `keycloak:8080`).

**Operator CLI** (as in §3 · §8 — actor `cli:portal`):

```sh
docker compose -f docker/compose.yaml --profile demo exec -T portal java -jar /app/portal.jar --spring.profiles.active=cli status --tenant=DEMO
docker compose -f docker/compose.yaml --profile demo exec -T portal java -jar /app/portal.jar --spring.profiles.active=cli recon run --tenant=DEMO --ym=202608
```

**Observability**: spans go via OTLP to Jaeger (<http://localhost:16686>, service `ga-agent-portal`, in-memory storage — gone when it is taken down). Only compose supplies the endpoint property (`management.opentelemetry.tracing.export.otlp.endpoint`) — the default in tests, CI and production is zero export (`OtlpExportOffIT`). Span names and keys are in the closed tables of `docs/지표.md` §4 · §4.1. Metrics are at `/actuator/prometheus` on the management port (inside the compose network — there is no Prometheus server; to look, call it from inside that container with `exec`). The same path on the application port is 404.

**Dates of the demo data** (plan P-20): the business dates of the stream are 2026-03 to 2028-02 (design document appendix B). "This month" on the screens and the reconciliation window follow the real date, so on any other date this month on the home screen is empty (there is no demo-only clock — zero demo code paths). Past months are viewed with the month selector on the statements screen.

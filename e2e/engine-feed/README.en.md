> 🌐 [한국어](README.md) · **English** — translation of `e2e/engine-feed/README.md` at commit `c3ac609`. The Korean original is canonical; where the two differ, the Korean text prevails.

# e2e/engine-feed — real engine feed E2E

An E2E connecting the portal and the real engine (`ga-commission-engine`) (portal Phase 3 directive D-16, Phase 1 acceptance review Q6). It is **outside the regular build** — so that the engine app and the Oracle Free container do not become dependencies of the portal build. The user runs it by hand, and the result is recorded in `RESULT.md` (written by the script) and in "Reproduction log" below. The acceptance review does not wait for this run.

## Running

```sh
ENGINE_REPO=/path/to/ai-comm/commission-system e2e/engine-feed/run.sh
```

Requirements: Docker, JDK 25 (Gradle toolchain), `openssl` · `curl`, the engine repository (after the E2 acceptance commit — one that has `localstack/up.sh`). Ports used: engine `8080` · Oracle `1521` (engine script), portal `8081` (`E2E_PORTAL_PORT`), PostgreSQL `127.0.0.1:55432` (`E2E_PG_PORT`). When finished (on success or failure), it stops the portal process, the issuer and the PostgreSQL container, and also takes the engine stack down with `up.sh down` (with `E2E_KEEP_ENGINE=1` the engine is left running).

## Steps

1. Engine `localstack/up.sh` — a fresh Oracle container + seed + the A-1 second-edition scenario + the engine app (tenant `GA01`). The portal only calls that command and does not duplicate its contents.
2. Disposable PostgreSQL container `ga-portal-e2e-pg` — image is `postgres-image` from `gradle/libs.versions.toml` (digest-pinned), `docker/postgres/init-roles.sql`, **no volume** (independent of the user's compose volumes).
3. Secret files (`.state/`, permission 600): a copy of the engine service token `tokens/GA01`, a 32-byte `policyKey` secret, a fictional subject `e2e-<무작위>` (무작위 = random).
4. Test issuer `./gradlew :portal-app:runTestIssuer --args=<.state>` — an in-process RS256 issuer (`TestIssuer`) writes a token for that subject (audience `portal-e2e`, 1 hour) and the issuer URL to `.state/`.
5. Start the portal bootJar (`PORTAL_INGEST_ENABLED=true`, reconciliation scheduler off `PORTAL_RECON_SCHEDULER_ENABLED=false` — the round runs via the CLI in step 8; Flyway applies all migrations).
6. Register tenant `GA01` (`engine_base_url` = local engine, `oidc_issuer` = test issuer) and the agent link (`A-1001`, `AGENT`) via **SQL files** (the same statements as `docs/운영.md` §1 · §2).
7. Wait for catch-up — until the cursor gets `caught_up_at` (a round that ended with `last_seq = headSeq` · `RUNNING` = `ingest_lag_seq` 0. Actuator exposes only `health`, so the same condition is checked in the DB instead of via metrics).
8. (portal Phase 7) Reconciliation — register the operator link of the OS user running the script (`cli:<OS 사용자>` (OS user), `OPERATOR`) via an **SQL file** (the same statement as `docs/운영.md` §3) and run, with the bootJar,
   `recon run --tenant=GA01 --ym=202608,202702` (`cli` profile, ingest off). Only the exit code goes to `.state/recon.exit`; the output goes to `.state/recon.log` (a one-line summary — no values).
9. Verdict `./gradlew :portal-app:e2eCheck` → `RESULT.md`:
   - 9 view tables = `portal-projection/src/test/resources/golden-views/a1/` (second edition — the same normal form as `ProjectionGoldenIT`: `tenant_id` excluded, timestamps in UTC). **No excluded columns** — the engine's `up.sh verify` compares the feed byte for byte with the contract copy `a1_portal_150000_e2.jsonl`, so `seq` · `eventId` · `occurredAt` must also match (the "exclusion rules" in the placeholder-stage README are no longer needed).
   - `GET /api/v1/me/earnings/202608` (test issuer token) returns 200, passes the contract `contracts/api/v1/portal-api.openapi.yaml`, and contains no raw policy number.
   - (portal Phase 5) A simulation pair: a new contract (the request of engine sample S1 — `WHOLE-LIFE-20Y` · 150,000 · 2026-08-01) and a cancellation impact (S5 — the ledger's `policyKey`, `CANCEL` · 2027-02-15 · installment 7) are sent through the portal API. The portal answers 200 only if the real engine's answer passes the schema of the engine contract copy (otherwise 502); the portal responses must pass the contract and contain no raw policy number, and `simulation_log` (GA01) must hold `NEW_CONTRACT` and `POLICY_EVENT` as `SUCCEEDED` with engine status 200. Values are not compared, since they may differ with the engine seed (the engine's `SimulationContractTest` compares values against the samples).
   - (portal Phase 6) `notification` (GA01) row count > 0 — notifications arise naturally in the transaction that applies the feed (`run.sh` unchanged). Only the counts per kind are recorded; keys and parameters are not read. The comparison with the hand calculation (A-1 — 3 `LIMIT_CUT` · 5 `LIMIT_FULL` · 6 `STMT_CONFIRMED` · 7 `PAYOUT` · 8 `POLICY_CANCEL` · 9 `CLAWBACK`) is done by `NotificationDerivationIT` with the contract copy.
   - (portal Phase 7) Reconciliation verdict ⑤: `recon run` exit code 0, last CLI round = checked 2 · matched 2 · new DRIFT 0 · unresolved 0 · undecidable 0 · `OK` (202608 is CONFIRMED — three comparisons: pre-tax, corrections, clawback offset; 202702 is ESTIMATE — two comparisons: clawback offset, corrections), `recon_result` (GA01) 0 rows.

Re-comparison after `ingest rebuild` (item 4 of the placeholder-stage README) is not included — it is outside the D-16 scope, and rebuild = golden is proven by `RebuildIT` with the contract copy. From portal Phase 7 on, the operator link is registered by step 8 (reconciliation).

## Secrets and personal data

Tokens, subject and secrets exist only in files under `.state/` (`.gitignore`) and are never carried in shell arguments, environment variables or output. The only environment variables passed to the portal are paths (`PORTAL_ENGINE_TOKEN_DIR` · `PORTAL_POLICY_KEY_SECRET_FILE`) and the DB URL. The PostgreSQL superuser password is a random value kept only in `.state/pg.env` (`--env-file`). Portal role credentials are the local defaults from `init-roles.sql` (only inside the disposable container). `RESULT.md` contains only the date, commits, hashes and row counts. On failure, look at `.state/portal.log` · `.state/issuer.log` — the next run deletes `.state/`.

## Reproduction log

(None — not run as of portal Phase 3. When run, commit `RESULT.md` and add one line here: date and time, engine and portal commits, verdict)

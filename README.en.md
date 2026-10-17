> 🌐 [한국어](README.md) · **English** — translation of `README.md` at commit `2649700`. The Korean original is canonical; where the two differ, the Korean text prevails.
>
> English documents: [design document](docs/en/design.md) · [operations](docs/en/operations.md) · [authorization matrix](docs/en/authz-matrix.md) · [real-engine E2E](e2e/engine-feed/README.en.md)

# ga-agent-portal

A read-only multi-tenant portal where agents of a GA check for themselves **"how much do I get this month · how much limit is left on this policy · how much will come out each month from now on · how much do I pay back if it is surrendered"**. Commission calculation, payout, clawback and settlement are done by the separate repository `ga-commission-engine`; the portal projects the engine's event stream from the agent's point of view and **only displays** it — it does not recalculate amounts (only integer sums and display-only ratios), attaches an estimated, confirmed or paid status to every amount, and its read model can be rebuilt from event seq 0 at any time. Shared value objects, the tenant-isolation foundation and the event Envelope core come from the platform artifacts published by `ga-disclosure` (`com.ga.platform` 0.2.0).

> **Disclaimer** — This is a learning and portfolio repository. Regulatory content (the 1200% rule, the deferral scheme, etc.) is an illustrative summary based on public materials; the actual standards are the original texts and authoritative interpretations of the Financial Services Commission (금융위원회) and the Financial Supervisory Service (금융감독원). All rates, amounts and thresholds are fictional example values. This system does not assert "compliance with legal requirements".

## Status

Phase 0 (skeleton) accepted (2026-10-16) — [plan](docs/phase-00-계획.md) · [approval](docs/phase-00-계획승인.md) · [report](docs/phase-00-보고서.md) · [acceptance review](docs/phase-00-수용심사.md). Tags `phase-0` (report, 1st edition) · `phase-0.1` (acceptance conditions applied).

Phase 1 (ingest) accepted (2026-10-17) — [directive](docs/phase-01-지시문.md) · [plan](docs/phase-01-계획.md) · [approval](docs/phase-01-계획승인.md) · [report](docs/phase-01-보고서.md) · [acceptance review](docs/phase-01-수용심사.md). Tag `phase-1`.

Phase 2 (projection) implemented, awaiting acceptance review — [directive](docs/phase-02-지시문.md) · [plan](docs/phase-02-계획.md) · [approval](docs/phase-02-계획승인.md) · [report](docs/phase-02-보고서.md). Tag `phase-2`. Contract v1.3 (A-1 = the engine's E2 ⓪a stream, byte-identical). Canonical design document [`docs/설계서.md`](docs/en/design.md) (v1.5), decision record [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Running

Requirements: a JDK (Gradle downloads toolchain 25), Docker (integration tests — without it the integration tests **fail**).

```bash
./gradlew build                      # unit, architecture and integration tests + build checks (zero SNAPSHOTs, pinned platform SHAs, declared test inputs)
./gradlew printPlatformArtifacts     # paths and SHA-256 of the 13 resolved platform 0.2.0 files (compare with the handoff §6 table)
```

The platform artifacts (`com.ga.platform:*:0.2.0`) are resolved

- **locally**: only from `mavenLocal()` — install them from ga-disclosure with `./gradlew publishToMavenLocal` (tag `platform-v0.2.0`).
- **in CI**: only from GitHub Packages (`hjryoo-ai/ga-disclosure`). Credentials are the repository secrets `GHP_USER` · `GHP_READ_TOKEN` (PAT classic, `read:packages`).

Either way, the build fails if the SHA-256 of a downloaded file differs from `gradle/verification-metadata.xml`.

## Ingest operations (Phase 1)

Polling that reads the engine feed per tenant runs only when `PORTAL_INGEST_ENABLED=true`. The tenant list comes via the dedicated role `portal_directory` (`PORTAL_DIRECTORY_USER` · `PORTAL_DIRECTORY_PASSWORD`); service tokens are per-tenant files `$PORTAL_ENGINE_TOKEN_DIR/<TENANT_ID>` (readable by the owner only — values are never written to the repository or logs). Operator commands for handling a stalled tenant:

```bash
java -jar portal-app.jar --spring.profiles.active=cli ingest status [--tenant=GA01]
java -jar portal-app.jar --spring.profiles.active=cli ingest resume --tenant=GA01 --from-seq=120
java -jar portal-app.jar --spring.profiles.active=cli ingest pause --tenant=GA01
java -jar portal-app.jar --spring.profiles.active=cli ingest rebuild --tenant=GA01   # Phase 2 — design document §6.7
```

Every invocation is recorded in `audit_access` (rejections too). `--from-seq` accepts only values at or below the last applied seq — skipping ahead is possible only via rebuild. **Authorization (Phase 3)**: the actor is the OS user who ran the command, and `cli:<OS user>` must be registered as `OPERATOR` in that tenant's `identity_link` (registration SQL in `docs/운영.md` §3). `--actor` has been removed (exit code 64). A rejection prints `T not authorized` + exit code 2.

`rebuild` (Phase 2) rebuilds that tenant's views from feed seq 1 in **a single transaction** — if it fails, nothing changes and the cursor becomes `FAILED(REBUILD_FAILED: …)` (exit code 1). Queries keep seeing the old rows during a rebuild. It does not start if the feed head exceeds `portal.ingest.rebuild.max-events` (default 50,000).

## Projection (Phase 2)

Projects 13 engine event types into 9 view tables (design document §4.2 table · §5). Amounts hold the engine's values as is, and the portal only does integer sums. Golden: `portal-projection/src/test/resources/golden-streams/` (streams; source hashes in its README) and `golden-views/` (expected rows — verified by hand). If the totals of a close or payout event differ from the portal's sum, that is not a poison event but a `DRIFT` for that month (metric `projection_drift_total`).

Free-form strings from the engine, such as `reason` in `earnings_line.basis`, are not agent or customer values and therefore not subject to the plaintext scan (they are engine rule descriptions) — they are stored, but not carried into logs or error messages.

## Query API (Phase 3)

The agent's own queries `/api/v1/me/**` (contract `contracts/api/v1/portal-api.openapi.yaml`, authorization table `docs/authz-matrix.md`). Authentication is an RS256 access token from the tenant's IdP — the tenant is determined only by the token issuer (`iss`), and the agent and role only by `identity_link` (no agentId in the path or query). Rejections are byte-identical (one 401, one 404, one 400 for query format). Policy numbers are not in responses — only `policyMasked` · `policyKey` (HMAC with a deployment secret). Operational registration (issuer, agent link, operator, `policyKey` secret) is in `docs/운영.md`. Required for deployment: `PORTAL_ISSUER_USER` · `PORTAL_ISSUER_PASSWORD` (dedicated role `portal_issuer`), `PORTAL_POLICY_KEY_SECRET_FILE`.

Real-engine E2E (outside the regular build, run by hand): `ENGINE_REPO=<path to the engine's commission-system> e2e/engine-feed/run.sh` — engine local stack → portal (ingest on) → 9 view tables = golden `a1`, one query API call passes the contract → `e2e/engine-feed/RESULT.md`. Procedure and secret handling in `e2e/engine-feed/README.md`.

## Modules

| Module | Role | Phase |
|---|---|---|
| `portal-domain` | Portal-specific value objects (`CalcId` · `PolicyNo` · `LedgerId` · `ScheduleId` · `CommType`) and status enums. No Spring or DB dependencies | 0 |
| `portal-projection` | Projectors (event → view) and notification derivation rules. No Spring or DB dependencies | 2·6 |
| `portal-ingest` | Event feed client, cursor, idempotent apply, rebuild | 1·2 |
| `portal-query` | Query API (`/api/v1/me/**`) and authorization | 3 |
| `portal-sim` | Engine simulation proxy, `simulation_log` | 5 |
| `portal-recon` | Reconciliation, DRIFT, observability metrics | 7 |
| `portal-infra` | PostgreSQL adapter, Flyway, RLS | 0 |
| `portal-app` | Spring Boot assembly, architecture tests | 0 |
| `portal-demo` | Demo tenant data generator | 8 |
| `web/` | React PWA | 4 |
| `contracts/` | Engine↔portal contracts (JSON Schema, OpenAPI), `shared/ga-disclosure/` copy of the shared core | 0 |

## License

MIT — [`LICENSE`](LICENSE).

> 🌐 [한국어](../운영.md) · **English** — translation of `docs/운영.md` at commit `2649700`. The Korean original is canonical; where the two differ, the Korean text prevails.

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

The actor of operator commands (`ingest status|resume|pause|rebuild`) is **the OS user who ran the command** — subject `cli:<OS user>` must be an `OPERATOR` · `ACTIVE` link of that tenant (`--actor` has been removed — using it gives exit code 64). An operator is not an agent, so it has no `agent_id` or `org_path`.

```sql
-- register-operator.sql
BEGIN;
SELECT set_config('app.tenant_id', '<TENANT_ID>', true);
INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
VALUES ('<TENANT_ID>', 'cli:<os-user>', NULL, 'OPERATOR', 'ACTIVE', NULL, now());
COMMIT;
```

- Register separately for each tenant. `ingest status` without a tenant checks each tenant, shows unregistered tenants as `T not authorized`, and exits with code 2 if there is at least one.
- A rejection is recorded in that tenant's `audit_access` as `AUTHZ_DENIED` (detail `NO_LINK` · `REVOKED` · `ROLE`, target `cli:ingest <command>`).
- Calling `/api` with a token for an `OPERATOR` link gives 404 (there is no agent).
- Revocation is `REVOKED`, as in 2.

## 4. `policyKey` secret — deployment and rotation

The response's `policyKey` = the first 16 characters of HMAC-SHA256(deployment secret, `tenant_id` + `U+001F` + `policy_no`) (design document §7). The secret is the file `portal.query.policy-key-secret-file` (environment `PORTAL_POLICY_KEY_SECRET_FILE`, default `/run/secrets/portal-policy-key`) — 32 or more random bytes, readable by the owner only. The web application does not start without this file.

- **Rotation changes every `policyKey`.** The UI (Phase 4) does not store keys — keys in bookmarks and saved filters yield empty results after rotation (not an error).
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

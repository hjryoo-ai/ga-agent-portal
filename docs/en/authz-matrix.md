> 🌐 [한국어](../authz-matrix.md) · **English** — translation of `docs/authz-matrix.md` at commit `c3ac609`. The Korean original is canonical; where the two differ, the Korean text prevails.

# API authorization matrix (canonical) — queries, simulations, notifications, inquiries

> Portal Phase 3 (directive D-11, plan ④, approval Q8). The `authz-matrix` block in this document is canonical, and `AuthzMatrixTest` checks it against the code **in both directions** —
> rows = the set of the controllers' `@GetMapping` · `@PostMapping` · `@PatchMapping` (Phase 6) templates = the set of `ApiAuthorization` keys (`com.ga.portal.domain.access`) = the set of `@UseCaseEntry` values. If any one of the three changes, the test fails.

Meaning of the columns:

- `AGENT` · `MANAGER` · `OPERATOR` — the caller's `identity_link.role` (not a token claim). `OPERATOR` is an operator CLI subject without an agent, so no `Caller` is created.
- `NO_LINK` — the tenant has no link for the subject. `REVOKED` — the link has been revoked.
- `SELF` = only that agent's own rows (another agent's key or a nonexistent key → the same 404). `404` = the same 404 bytes regardless of reason, audit `AUTHZ_DENIED` (detail = `NO_LINK` · `REVOKED` · `ROLE` · `MISSING`).
- Authentication failures (token, issuer, signature, expiry, audience, tenant) are all the same 401 before this table is reached (no audit, metrics only).

```authz-matrix
endpoint,AGENT,MANAGER,OPERATOR,NO_LINK,REVOKED
GET /api/v1/me,SELF,404,404,404,404
GET /api/v1/me/earnings,SELF,404,404,404,404
GET /api/v1/me/earnings/{ym},SELF,404,404,404,404
GET /api/v1/me/earnings/{ym}/lines,SELF,404,404,404,404
GET /api/v1/me/earnings/lines/{calcId},SELF,404,404,404,404
GET /api/v1/me/limits,SELF,404,404,404,404
GET /api/v1/me/limits/{ledgerId},SELF,404,404,404,404
GET /api/v1/me/deferrals,SELF,404,404,404,404
POST /api/v1/me/simulations/new-contract,SELF,404,404,404,404
POST /api/v1/me/simulations/policy-event,SELF,404,404,404,404
GET /api/v1/me/notifications,SELF,404,404,404,404
PATCH /api/v1/me/notifications/{id}/read,SELF,404,404,404,404
GET /api/v1/me/inquiries,SELF,404,404,404,404
POST /api/v1/me/inquiries,SELF,404,404,404,404
GET /api/v1/me/inquiries/{id},SELF,404,404,404,404
```

The two simulation rows (portal Phase 5, contract 1.1.0): `SELF` = the engine is asked only as the caller's own agent (the request body has no agent). A policy event's `policyKey` is resolved only within that agent's set of line policy numbers; another agent's or a nonexistent key gets the same 404 (`MISSING` — zero `simulation_log` rows and zero engine calls, `SimulationAuthzIT`).

The five notification and inquiry rows (portal Phase 6, contract 1.2.0): `SELF` = only the calling agent's notifications and inquiries (every statement has `agent_id = :agentId`). Another agent's, a nonexistent or a malformed id gets the same 404 (`MISSING` — mark-as-read updates zero rows, inquiry detail selects zero rows). An inquiry's `calcId` is resolved only within that agent's lines; otherwise the same 404 (`MISSING` — zero rows, before the rate-limit check). Successful writes (mark as read, submit) leave no audit row — the row itself is the record (plan Q6). Answering and closing are not API operations but the operator CLI (`OPERATOR`).

## Unauthenticated paths (closed list — portal Phase 8)

The `public-paths` block below is the canonical list of `GET` paths open without authentication (plan Q2 · P-12, condition A). `PublicPathsTest` checks it **in both directions** against the security configuration `ApiSecurityConfiguration.PUBLIC_PATHS` (the `permitAll` of the remaining chain). These paths are outside the table above — there is no `Caller` and no audit row.

- The three health paths — status only (no components or details).
- `/auth/config` (contract 1.3.0) — two fields, the issuer and the public client id, of the `tenant.web_origin` that is **exactly equal** to the request origin (`Origin`, or scheme + `Host` if absent). An unregistered origin gets the same bytes as the 404 above (origins cannot be enumerated). Authentication headers are not looked at.
- Screen paths (`/`, `/statements` …, including `/auth/callback`, where the PKCE popup returns) are not in this list — they are the closed list of the screen chain, which serves only the same document (`index.html`) (`ScreenConfiguration.SCREEN_PATHS` = `web/src/routes.ts`, `WebScreensIT`).

```public-paths
/actuator/health
/actuator/health/readiness
/actuator/health/liveness
/auth/config
```

`MANAGER` is the role for v2 organization queries (`/api/v1/org/**`) — rows for it are added to this table then (design document §14.1). Currently `/api/v1/me/**` is `AGENT` only.

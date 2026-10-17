> 🌐 [한국어](../authz-matrix.md) · **English** — translation of `docs/authz-matrix.md` at commit `2649700`. The Korean original is canonical; where the two differ, the Korean text prevails.

# Query API authorization matrix (canonical)

> Portal Phase 3 (directive D-11, plan ④, approval Q8). The `authz-matrix` block in this document is canonical, and `AuthzMatrixTest` checks it against the code **in both directions** —
> rows = the set of the controllers' `@GetMapping` templates = the set of `QueryAuthorization` keys = the set of `@UseCaseEntry` values. If any one of the three changes, the test fails.

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
```

`MANAGER` is the role for v2 organization queries (`/api/v1/org/**`) — rows for it are added to this table then (design document §14.1). Currently `/api/v1/me/**` is `AGENT` only.

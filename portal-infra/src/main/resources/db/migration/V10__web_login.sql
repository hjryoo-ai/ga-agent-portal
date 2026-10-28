-- =============================================================================================
-- V10__web_login.sql — 포털 Phase 8(데모 테넌트·통합 실행) ③ Keycloak + PKCE. 설계서 §9(v1.11), 계획 docs/phase-08-계획.md P-13·Q2
-- (승인 2026-10-28 — 돌이킬 수 없는 결정 5, 조건 A).
--
-- 화면이 로그인할 발급자를 아는 방법(GET /auth/config): 요청 출처(web_origin) 정확 일치 → 그 테넌트의 발급자·클라이언트 id.
-- ① web_origin: 스킴 + 호스트[:포트]만(경로·꼬리 슬래시 없음). 출처 하나는 테넌트 하나(UNIQUE).
-- ② oidc_client_id: 공개 클라이언트(PKCE S256) id. 비밀이 아니다 — 화면이 IdP에 보내는 값.
-- ③ 둘은 함께 있거나 함께 없다. 있으면 oidc_issuer도 있다(로그인할 발급자 없이 출처만 등록할 수 없다).
--    둘 다 없는 테넌트(지금 행 전부·하네스 테넌트)는 /auth/config 404이고 화면 CSP는 지금 그대로다.
-- ④ portal_issuer(V5)에 두 열 SELECT를 더한다 — 정책 tenant_issuer(USING true)는 그대로, 다른 열 권한은 여전히 0(IssuerDirectoryIT).
-- =============================================================================================

ALTER TABLE tenant ADD COLUMN web_origin TEXT, ADD COLUMN oidc_client_id TEXT;
ALTER TABLE tenant ADD CONSTRAINT uq_tenant_web_origin UNIQUE (web_origin);
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_web_origin CHECK (web_origin ~ '^https?://[^/[:space:]]+$');
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_oidc_client_id CHECK (oidc_client_id ~ '^[A-Za-z0-9._-]{1,128}$');
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_web_login_pair CHECK ((web_origin IS NULL) = (oidc_client_id IS NULL));
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_web_login_issuer CHECK (web_origin IS NULL OR oidc_issuer IS NOT NULL);

GRANT SELECT (web_origin, oidc_client_id) ON TABLE tenant TO portal_issuer;

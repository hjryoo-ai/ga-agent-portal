-- =============================================================================================
-- seed-demo.sql — 데모 테넌트 등록(포털 Phase 8 지시문 ②, 계획 P-13·P-14·P-23). compose 서비스 `seed`가 포털이 뜬 뒤(Flyway 끝) portal_migrator로
-- 한 번 돌린다. 문장은 docs/운영.md §1~§3의 운영 SQL 그대로(테넌트를 바인딩하고 넣는다 — RLS). 다시 돌려도 같다(ON CONFLICT DO NOTHING).
-- 손으로 고치지 않는다 — docker/keycloak/generate-realm.py의 사용자 id와 짝이고 DemoComposeFilesTest가 대조한다.
--   테넌트 DEMO "데모GA": 엔진 = 데모 엔진 대역(http://demo:8090), 발급자 = compose Keycloak 렐름(브라우저와 포털 컨테이너가 같은 주소 —
--   socat 중계), 대상 portal-demo, 웹 출처 http://localhost:8080 → 공개 클라이언트 portal-web(GET /auth/config).
--   설계사 30명(A-2001~A-2030, subject = 렐름 사용자 id), 조직 경로는 포털 사실(인가용 — 다섯 명씩 T1~T6, B1·B2).
--   운영자 cli:portal(포털 이미지의 OS 사용자 — docker compose exec portal … --spring.profiles.active=cli).
--   params: 기본 셋(§14.0) + 폴링 5초(docs/운영.md §5 범위 5~30) — 포털 인제스트 쪽 한 쪽 1,000건(compose 명령 인자)과 함께 첫 동기화 약 90초.
-- =============================================================================================
BEGIN;
SELECT set_config('app.tenant_id', 'DEMO', true);
INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule, params, oidc_issuer, oidc_audience, web_origin, oidc_client_id)
VALUES ('DEMO', '데모GA', 'http://demo:8090', 'ACTIVE', 'NEXT_MONTH_D10',
        '{"org_member_lines_visible": false, "forecast_scenario_enabled": false, "notification_digest": false, "poll_interval_seconds": 5}'::jsonb,
        'http://localhost:8180/realms/demo', 'portal-demo', 'http://localhost:8080', 'portal-web')
ON CONFLICT (tenant_id) DO NOTHING;
INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at) VALUES
    ('DEMO', '00000000-0000-4000-8000-000000002001', 'A-2001', 'AGENT', 'ACTIVE', '/H1/B1/T1', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002002', 'A-2002', 'AGENT', 'ACTIVE', '/H1/B1/T1', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002003', 'A-2003', 'AGENT', 'ACTIVE', '/H1/B1/T1', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002004', 'A-2004', 'AGENT', 'ACTIVE', '/H1/B1/T1', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002005', 'A-2005', 'AGENT', 'ACTIVE', '/H1/B1/T1', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002006', 'A-2006', 'AGENT', 'ACTIVE', '/H1/B1/T2', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002007', 'A-2007', 'AGENT', 'ACTIVE', '/H1/B1/T2', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002008', 'A-2008', 'AGENT', 'ACTIVE', '/H1/B1/T2', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002009', 'A-2009', 'AGENT', 'ACTIVE', '/H1/B1/T2', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002010', 'A-2010', 'AGENT', 'ACTIVE', '/H1/B1/T2', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002011', 'A-2011', 'AGENT', 'ACTIVE', '/H1/B1/T3', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002012', 'A-2012', 'AGENT', 'ACTIVE', '/H1/B1/T3', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002013', 'A-2013', 'AGENT', 'ACTIVE', '/H1/B1/T3', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002014', 'A-2014', 'AGENT', 'ACTIVE', '/H1/B1/T3', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002015', 'A-2015', 'AGENT', 'ACTIVE', '/H1/B1/T3', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002016', 'A-2016', 'AGENT', 'ACTIVE', '/H1/B2/T4', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002017', 'A-2017', 'AGENT', 'ACTIVE', '/H1/B2/T4', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002018', 'A-2018', 'AGENT', 'ACTIVE', '/H1/B2/T4', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002019', 'A-2019', 'AGENT', 'ACTIVE', '/H1/B2/T4', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002020', 'A-2020', 'AGENT', 'ACTIVE', '/H1/B2/T4', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002021', 'A-2021', 'AGENT', 'ACTIVE', '/H1/B2/T5', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002022', 'A-2022', 'AGENT', 'ACTIVE', '/H1/B2/T5', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002023', 'A-2023', 'AGENT', 'ACTIVE', '/H1/B2/T5', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002024', 'A-2024', 'AGENT', 'ACTIVE', '/H1/B2/T5', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002025', 'A-2025', 'AGENT', 'ACTIVE', '/H1/B2/T5', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002026', 'A-2026', 'AGENT', 'ACTIVE', '/H1/B2/T6', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002027', 'A-2027', 'AGENT', 'ACTIVE', '/H1/B2/T6', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002028', 'A-2028', 'AGENT', 'ACTIVE', '/H1/B2/T6', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002029', 'A-2029', 'AGENT', 'ACTIVE', '/H1/B2/T6', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', '00000000-0000-4000-8000-000000002030', 'A-2030', 'AGENT', 'ACTIVE', '/H1/B2/T6', TIMESTAMPTZ '2026-10-28 00:00:00+00'),
    ('DEMO', 'cli:portal', NULL, 'OPERATOR', 'ACTIVE', NULL, TIMESTAMPTZ '2026-10-28 00:00:00+00')
ON CONFLICT (tenant_id, subject) DO NOTHING;
COMMIT;

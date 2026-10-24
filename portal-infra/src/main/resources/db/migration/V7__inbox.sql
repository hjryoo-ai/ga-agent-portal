-- =============================================================================================
-- V7__inbox.sql — 포털 Phase 6(알림 파생·이의제기). 설계서 §5(v1.9), 지시문 docs/phase-06-지시문.md ③, 계획 docs/phase-06-계획.md ⑤
-- (승인 2026-10-24).
--
-- notification·inquiry는 V1이 만들었고 RLS·권한은 V2 그대로다(새 표 0). 여기서는 열·제약·인덱스만 더한다.
-- 전제: 두 표는 운영 DB에서 비어 있다 — Phase 5까지 파생기는 Noop이었고 문의 API가 없었다. 기존 행이 아래 CHECK를 어기면 이 마이그레이션이
-- 실패한다(조용히 넘기지 않는다 — 고칠 행을 먼저 본다).
--
-- notification
-- ① params JSONB — 알림 문장의 자리(계획 Q1): 서버는 문장을 만들지 않는다. params는 type별 닫힌 모양(계약 1.2.0 Notification의 oneOf 변이)이고
--    금액이 없다. 화면이 messages.ko.json의 템플릿으로 문장을 만든다.
-- ② title·body — 열은 남기되 죽인다(NULL만, 응답에 내지 않는다 — 승인 §2 Q1).
-- ③ type은 닫힌 열넷(Java NotificationType과 같다), link는 화면 경로 넷 중 하나의 형식(계획 Q20 — 키 조각은 서버가 퍼센트 부호화).
-- ④ 목록 인덱스 (tenant_id, agent_id, created_at DESC).
--
-- inquiry
-- ⑤ category는 닫힌 다섯(Java InquiryCategory), OTHER가 아니면 calc_id 필수(계획 Q5).
-- ⑥ message 1~1,000 코드 포인트(char_length — 계획 Q27)·공백만은 안 된다, answer 1~4,000, answered_by는 운영 CLI 주체(cli:<OS 사용자>).
-- ⑦ 상태별 열: OPEN은 답변 셋이 모두 NULL, ANSWERED는 모두 있음, CLOSED는 둘 중 하나(열린 채 닫을 수 있다).
-- ⑧ 목록·24시간 창(레이트 리밋) 인덱스 (tenant_id, agent_id, created_at DESC).
-- =============================================================================================

ALTER TABLE notification
    ADD COLUMN params JSONB NOT NULL DEFAULT '{}' CONSTRAINT ck_notification_params CHECK (jsonb_typeof(params) = 'object'),
    ALTER COLUMN title DROP NOT NULL,
    ALTER COLUMN body DROP NOT NULL;

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_text CHECK (title IS NULL AND body IS NULL),
    ADD CONSTRAINT ck_notification_type CHECK (type IN ('LIMIT_WARN', 'LIMIT_CRIT', 'LIMIT_FULL', 'LIMIT_REBASE', 'LIMIT_CUT', 'CLAWBACK',
                                                       'DEFER_HELD', 'DEFER_CANCELLED', 'POLICY_PAYMENT_OVERDUE', 'POLICY_LAPSE',
                                                       'POLICY_CANCEL', 'STMT_CONFIRMED', 'PAYOUT', 'INQUIRY_ANSWERED')),
    ADD CONSTRAINT ck_notification_link CHECK (link IS NULL
        OR link ~ '^/(lines|limits)/[A-Za-z0-9._~%-]+$'
        OR link ~ '^/statements/[0-9]{4}(0[1-9]|1[0-2])$'
        OR link ~ '^/inquiries/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');

CREATE INDEX ix_notification_agent_created ON notification (tenant_id, agent_id, created_at DESC);

ALTER TABLE inquiry
    ADD CONSTRAINT ck_inquiry_category CHECK (category IN ('AMOUNT', 'LIMIT', 'CLAWBACK', 'DEFERRAL', 'OTHER')),
    ADD CONSTRAINT ck_inquiry_calc_id CHECK ((calc_id IS NULL OR char_length(calc_id) BETWEEN 1 AND 128)
                                             AND (category = 'OTHER' OR calc_id IS NOT NULL)),
    ADD CONSTRAINT ck_inquiry_message CHECK (char_length(message) BETWEEN 1 AND 1000 AND message ~ '\S'),
    ADD CONSTRAINT ck_inquiry_answer CHECK (answer IS NULL OR (char_length(answer) BETWEEN 1 AND 4000 AND answer ~ '\S')),
    ADD CONSTRAINT ck_inquiry_answered_by CHECK (answered_by IS NULL OR answered_by ~ '^cli:[A-Za-z0-9][A-Za-z0-9._@-]{0,63}$'),
    ADD CONSTRAINT ck_inquiry_columns CHECK (
        (status = 'OPEN' AND answer IS NULL AND answered_at IS NULL AND answered_by IS NULL)
        OR (status = 'ANSWERED' AND answer IS NOT NULL AND answered_at IS NOT NULL AND answered_by IS NOT NULL)
        OR (status = 'CLOSED' AND ((answer IS NULL AND answered_at IS NULL AND answered_by IS NULL)
                                   OR (answer IS NOT NULL AND answered_at IS NOT NULL AND answered_by IS NOT NULL))));

CREATE INDEX ix_inquiry_agent_created ON inquiry (tenant_id, agent_id, created_at DESC);

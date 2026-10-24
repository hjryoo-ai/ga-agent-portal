package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.infra.testing.SeedData;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 알림함·이의제기 API IT의 공통 조립(포털 Phase 6 ⑥ — 3단계): 테넌트 셋.
 * <ul>
 *   <li>{@link #T6}: 기본 문의 한도(앱 기본 20). 설계사 A·B(라인 하나씩)·조직장·운영자 링크. A에게 알림 여섯 변이 하나씩(파생은 4단계 — 여기서는 시험이 행을 직접 넣는다),
 *       B에게 하나.</li>
 *   <li>{@link #T6R}: 문의 한도 3({@code params.inquiry_rate_per_day}) — 순차 리밋 시험.</li>
 *   <li>{@link #T6B}: 문의 한도 3 — 동시 리밋 시험만(빈 창으로 시작한다).</li>
 * </ul>
 * subject·설계사 ID·증권번호·문의 본문 센티널은 허구이고 평문 스캔 센티널({@code pii-sentinels.properties} {@code inbox})이다.
 */
abstract class InboxIntegrationTest extends ApiIntegrationTest {

    static final String NOTIFICATIONS = "/api/v1/me/notifications";
    static final String INQUIRIES = "/api/v1/me/inquiries";

    static final TestIssuer ISSUER_6 = new TestIssuer("ph6-inbox");
    static final TestIssuer ISSUER_6R = new TestIssuer("ph6-rate");
    static final TestIssuer ISSUER_6B = new TestIssuer("ph6-burst");
    static final String AUD_6 = "portal-ph6-inbox";
    static final String AUD_6R = "portal-ph6-rate";
    static final String AUD_6B = "portal-ph6-burst";
    static final TenantId T6 = TenantId.of(SeedData.uniqueTenant("PH6I"));
    static final TenantId T6R = TenantId.of(SeedData.uniqueTenant("PH6R"));
    static final TenantId T6B = TenantId.of(SeedData.uniqueTenant("PH6B"));

    static final String SUB_6A = "sub-ph6-agent-a-Wk11";
    static final String SUB_6B = "sub-ph6-agent-b-Wk12";
    static final String SUB_6M = "sub-ph6-manager-Wk13";
    static final String SUB_6O = "cli:ph6-ops-Wk14";
    static final String SUB_6R = "sub-ph6-rate-Wk15";
    static final String SUB_6U = "sub-ph6-burst-Wk16";
    static final String SUB_6I = "sub-ph6-iso-Wk17";
    static final String AGENT_6A = "PH6-A-8811";
    static final String AGENT_6B = "PH6-B-8812";
    static final String AGENT_6M = "PH6-M-8813";
    static final String AGENT_6R = "PH6-R-8815";
    static final String AGENT_6U = "PH6-U-8816";
    static final String AGENT_6I = "PH6-I-8817";
    static final String CALC_6A = "PH6-A-8811-1";
    static final String CALC_6B = "PH6-B-8812-1";
    static final String CALC_6I = "PH6-I-8817-1";
    static final String POLICY_6A = "POL-PH6-A-8811-01";
    static final String POLICY_6B = "POL-PH6-B-8812-01";
    static final String POLICY_6I = "POL-PH6-I-8817-01";
    static final int RATE = 3;

    /** A의 알림 여섯(변이마다 하나) — 넣은 순서 = 만든 시각 오름차순, 목록은 내림차순. */
    static final List<Seeded> A_NOTIFICATIONS = List.of(
            new Seeded(NotificationType.LIMIT_WARN, "LIMIT_WARN:PH6-A-L1:3", "/limits/PH6-A-L1", "{\"ledgerId\":\"PH6-A-L1\",\"policyMasked\":\"*************1-01\"}",
                    "2026-10-20 01:00:00+00"),
            new Seeded(NotificationType.LIMIT_CUT, "LIMIT_CUT:" + CALC_6A, "/lines/" + CALC_6A,
                    "{\"calcId\":\"" + CALC_6A + "\",\"policyMasked\":\"*************1-01\",\"closeYm\":\"202610\"}", "2026-10-20 02:00:00+00"),
            new Seeded(NotificationType.DEFER_HELD, "DEFER_HELD:PH6-A-S1:5", "/lines/" + CALC_6A,
                    "{\"calcId\":\"" + CALC_6A + "\",\"policyMasked\":\"*************1-01\",\"dueYm\":\"202611\"}", "2026-10-20 03:00:00+00"),
            new Seeded(NotificationType.POLICY_CANCEL, "POLICY_CANCEL:00000000-0000-4000-8000-000000000601", null,
                    "{\"policyMasked\":\"*************1-01\",\"occurredOn\":\"2026-10-19\"}", "2026-10-20 04:00:00+00"),
            new Seeded(NotificationType.STMT_CONFIRMED, "STMT_CONFIRMED:202609:7", "/statements/202609", "{\"ym\":\"202609\"}", "2026-10-20 05:00:00+00"),
            new Seeded(NotificationType.INQUIRY_ANSWERED, "INQUIRY:00000000-0000-4000-8000-000000000602", "/inquiries/00000000-0000-4000-8000-000000000602",
                    "{\"inquiryId\":\"00000000-0000-4000-8000-000000000602\"}", "2026-10-20 06:00:00+00"));
    static final Seeded B_NOTIFICATION = new Seeded(NotificationType.LIMIT_CUT, "LIMIT_CUT:" + CALC_6B, "/lines/" + CALC_6B,
            "{\"calcId\":\"" + CALC_6B + "\",\"policyMasked\":\"*************1-01\",\"closeYm\":\"202610\"}", "2026-10-20 02:30:00+00");

    record Seeded(NotificationType type, String dedupKey, String link, String params, String createdAt) {
        String id(TenantId t, String agent) {
            return NotificationRecord.id(t.value(), agent, dedupKey).toString();
        }
    }

    static {
        inboxTenant(T6, ISSUER_6, AUD_6, null);
        inboxTenant(T6R, ISSUER_6R, AUD_6R, RATE);
        inboxTenant(T6B, ISSUER_6B, AUD_6B, RATE);
        link(T6, SUB_6A, AGENT_6A, "AGENT", "ACTIVE");
        link(T6, SUB_6B, AGENT_6B, "AGENT", "ACTIVE");
        link(T6, SUB_6M, AGENT_6M, "MANAGER", "ACTIVE");
        link(T6, SUB_6O, null, "OPERATOR", "ACTIVE");
        link(T6, SUB_6I, AGENT_6I, "AGENT", "ACTIVE");
        link(T6R, SUB_6R, AGENT_6R, "AGENT", "ACTIVE");
        link(T6B, SUB_6U, AGENT_6U, "AGENT", "ACTIVE");
        DB.seed(T6.value(), c -> {
            line(c, AGENT_6A, CALC_6A, POLICY_6A);
            line(c, AGENT_6B, CALC_6B, POLICY_6B);
            line(c, AGENT_6I, CALC_6I, POLICY_6I);
            for (Seeded n : A_NOTIFICATIONS) {
                notification(c, AGENT_6A, n);
            }
            notification(c, AGENT_6B, B_NOTIFICATION);
        });
    }

    private static void inboxTenant(TenantId t, TestIssuer issuer, String audience, Integer ratePerDay) {
        seedTenant(t, issuer.issuer(), audience);
        if (ratePerDay != null) {
            DB.seed(t.value(), c -> SeedData.exec(c, "UPDATE tenant SET params = jsonb_build_object('inquiry_rate_per_day', ?::int) WHERE tenant_id = ?",
                    ratePerDay, t.value()));
        }
    }

    private static void line(java.sql.Connection c, String agent, String calcId, String policy) throws java.sql.SQLException {
        SeedData.exec(c, """
                INSERT INTO earnings_line (tenant_id, calc_id, agent_id, policy_no, product_key, comm_type, amount, close_ym, limit_cut_amt, basis,
                                           policy_summary, source_seq)
                VALUES (?, ?, ?, ?, 'WHOLE-LIFE-20Y', 'FY_COMM', 1, '202610', 0, '{"ruleVersionIds":[]}'::jsonb,
                        '{"contractDate":"2026-08-01","monthlyPremium":150000}'::jsonb, 1)
                """, T6.value(), calcId, agent, policy);
    }

    static void notification(java.sql.Connection c, String agent, Seeded n) throws java.sql.SQLException {
        SeedData.exec(c, """
                INSERT INTO notification (tenant_id, notification_id, agent_id, type, severity, dedup_key, link, params, created_at)
                VALUES (?, ?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?::timestamptz)
                """, T6.value(), n.id(T6, agent), agent, n.type().name(), n.type().severity().name(), n.dedupKey(), n.link(), n.params(), n.createdAt());
    }

    /** 문의 본문 센티널(허구 — 센티널 파일의 {@code inbox} 중 {@code 문의센티널-}로 시작하는 값). */
    static List<String> bodySentinels() {
        Properties p = new Properties();
        try (InputStream in = InboxIntegrationTest.class.getClassLoader().getResourceAsStream("pii-sentinels.properties")) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.of(p.getProperty("inbox").split(",")).stream().map(String::trim).filter(s -> s.startsWith("문의센티널-")).toList();
    }

    String t6(String subject) {
        return ISSUER_6.token(subject, AUD_6);
    }

    HttpResponse<byte[]> patch(String path, String token) {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.noBody()));
    }

    HttpResponse<byte[]> postBytes(String path, String token, String contentType, byte[] body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return send(b);
    }

    static String inquiryJson(String category, String calcId, String message) {
        return "{\"category\":\"" + category + "\"" + (calcId == null ? "" : ",\"calcId\":\"" + calcId + "\"") + ",\"message\":\"" + message + "\"}";
    }

    /** 문의 행(시험 판독 — 앱 롤로, RLS 안에서). 본문은 비교만 하고 출력하지 않는다. */
    record InquiryRow(String id, String agentId, String calcId, String category, String message, String status) {
        @Override
        public String toString() {
            return "InquiryRow[" + id + ", " + status + "]";
        }
    }

    static List<InquiryRow> inquiryRows(TenantId t) {
        return DB.asApp(t.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT inquiry_id::text, agent_id, calc_id, category, message, status FROM inquiry WHERE tenant_id = ? ORDER BY created_at, inquiry_id")) {
                ps.setString(1, t.value());
                List<InquiryRow> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new InquiryRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
                    }
                }
                return out;
            }
        });
    }

    /** 알림 행의 읽음 시각(없으면 null). */
    static String readAt(String notificationId) {
        return DB.asApp(T6.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT read_at::text FROM notification WHERE tenant_id = ? AND notification_id = ?::uuid")) {
                ps.setString(1, T6.value());
                ps.setString(2, notificationId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }
}

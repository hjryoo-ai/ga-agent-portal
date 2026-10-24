package com.ga.portal.infra;

import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V7(포털 Phase 6 계획 ⑤): {@code notification}의 {@code params}(객체)·죽인 {@code title}·{@code body}·닫힌 {@code type}·화면 경로 넷의 {@code link},
 * {@code inquiry}의 닫힌 {@code category}·라인 필수·본문 길이(코드 포인트)·상태별 답변 열·운영 주체 형식. 받는 행과 거부하는 행을 둘 다 본다.
 */
class InboxSchemaIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final String TS = "TIMESTAMPTZ '2026-10-24 00:00:00+00'";
    private static final String UUID_A = "'6f1c2b9e-0c1d-4e5f-8a9b-0c1d2e3f4a5b'";

    private static String tenant(String prefix) {
        String t = SeedData.uniqueTenant(prefix);
        DB.seed(t, c -> SeedData.exec(c, SeedData.ROWS.get("tenant"), t));
        return t;
    }

    private static String notification(String id, String type, String dedup, String link, String params) {
        return "INSERT INTO notification (tenant_id, notification_id, agent_id, type, severity, dedup_key, link, params, created_at) VALUES (?, '"
                + id + "', 'A0001', '" + type + "', 'INFO', '" + dedup + "', " + link + ", " + params + ", " + TS + ")";
    }

    @Test
    void notificationsWithTheClosedTypesAndScreenPathsAreAccepted() {
        String t = tenant("NTFY");
        DB.seed(t, c -> {
            SeedData.exec(c, notification("00000000-0000-4000-8000-000000000001", "LIMIT_FULL", "LIMIT_FULL:L1:5", "'/limits/L1'",
                    "'{\"ledgerId\":\"L1\",\"policyMasked\":\"**\"}'"), t);
            SeedData.exec(c, notification("00000000-0000-4000-8000-000000000002", "LIMIT_CUT", "LIMIT_CUT:C%2F1", "'/lines/C%2F1'", "'{}'"), t);
            SeedData.exec(c, notification("00000000-0000-4000-8000-000000000003", "PAYOUT", "PAYOUT:202608", "'/statements/202608'", "'{}'"), t);
            SeedData.exec(c, notification("00000000-0000-4000-8000-000000000004", "INQUIRY_ANSWERED", "INQUIRY:x", "'/inquiries/"
                    + UUID_A.replace("'", "") + "'", "'{}'"), t);
            SeedData.exec(c, notification("00000000-0000-4000-8000-000000000005", "POLICY_CANCEL", "POLICY_CANCEL:e1", "NULL", "'{}'"), t);
        });
        assertThat(DB.<String>asApp(t, c -> string(c, "SELECT count(*) || ':' || count(*) FILTER (WHERE title IS NULL AND body IS NULL) FROM notification "
                + "WHERE tenant_id = ?", t))).isEqualTo("5:5");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LIMIT|'/limits/L1'|'{}'",                       // 닫힌 종류 밖(V1 시드의 옛 값)
            "PAYOUT|'/statements/202613'|'{}'",              // 월 형식 밖
            "PAYOUT|'/statements/202608/x'|'{}'",            // 경로 넷 밖
            "LIMIT_CUT|'/lines/C/1'|'{}'",                   // 부호화하지 않은 슬래시
            "LIMIT_CUT|'https://evil.invalid/lines/C1'|'{}'",// 밖 출처
            "LIMIT_CUT|'//evil.invalid/x'|'{}'",             // 프로토콜 상대 주소
            "INQUIRY_ANSWERED|'/inquiries/not-a-uuid'|'{}'", // 문의 id 형식 밖
            "LIMIT_CUT|'/simulations'|'{}'",                 // 다른 화면
            "LIMIT_CUT|NULL|'[]'",                           // params가 객체가 아니다
    })
    void anInvalidNotificationIsRejected(String spec) {
        String[] p = spec.split("\\|");
        String t = tenant("NTFYX");
        assertThatThrownBy(() -> DB.seed(t, c -> SeedData.exec(c, notification("00000000-0000-4000-8000-0000000000aa", p[0], "K", p[1], p[2]), t)))
                .hasMessageContaining("23514");
    }

    @Test
    void titleAndBodyAreDead() {
        String t = tenant("NTFYT");
        assertThatThrownBy(() -> DB.seed(t, c -> SeedData.exec(c, "INSERT INTO notification (tenant_id, notification_id, agent_id, type, severity, "
                + "dedup_key, title, body, created_at) VALUES (?, " + UUID_A + ", 'A0001', 'PAYOUT', 'INFO', 'PAYOUT:202608', 't', 'b', " + TS + ")", t)))
                .hasMessageContaining("23514");
    }

    private static String inquiry(String id, String category, String calcId, String message, String status, String answer) {
        return "INSERT INTO inquiry (tenant_id, inquiry_id, agent_id, calc_id, category, message, status, created_at, answered_at, answer, answered_by) "
                + "VALUES (?, '" + id + "', 'A0001', " + calcId + ", '" + category + "', " + message + ", '" + status + "', " + TS + ", "
                + (answer == null ? "NULL, NULL, NULL" : TS + ", " + answer + ", 'cli:ops-kim'") + ")";
    }

    @Test
    void inquiriesInEveryStatusAreAccepted() {
        String t = tenant("INQ");
        DB.seed(t, c -> {
            SeedData.exec(c, inquiry("00000000-0000-4000-8000-000000000011", "AMOUNT", "'C1'", "'금액이 달라요'", "OPEN", null), t);
            SeedData.exec(c, inquiry("00000000-0000-4000-8000-000000000012", "OTHER", "NULL", "repeat('가', 1000)", "ANSWERED", "'확인했습니다'"), t);
            SeedData.exec(c, inquiry("00000000-0000-4000-8000-000000000013", "OTHER", "NULL", "repeat(U&'\\+01F600', 1000)", "CLOSED", null), t);
            SeedData.exec(c, inquiry("00000000-0000-4000-8000-000000000014", "DEFERRAL", "'C1'", "'x'", "CLOSED", "'답변'"), t);
        });
        assertThat(DB.<Integer>asApp(t, c -> Integer.parseInt(string(c, "SELECT count(*) FROM inquiry WHERE tenant_id = ?", t)))).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LINE|'C1'|'m'|OPEN|",                             // 닫힌 분류 밖(V1 시드의 옛 값)
            "AMOUNT|NULL|'m'|OPEN|",                           // 라인 없는 금액 문의
            "OTHER|NULL|''|OPEN|",                             // 빈 본문
            "OTHER|NULL|'   '|OPEN|",                          // 공백만
            "OTHER|NULL|repeat('가', 1001)|OPEN|",             // 1,001 코드 포인트
            "OTHER|NULL|'m'|OPEN|'답'",                        // 열린 문의에 답변
            "OTHER|NULL|'m'|ANSWERED|",                        // 답변 없는 ANSWERED
            "OTHER|NULL|'m'|ANSWERED|repeat('가', 4001)",      // 답변 상한 밖
            "OTHER|repeat('c', 129)|'m'|OPEN|",               // 라인 키 상한 밖
    })
    void anInvalidInquiryIsRejected(String spec) {
        String[] p = spec.split("\\|", -1);
        String t = tenant("INQX");
        assertThatThrownBy(() -> DB.seed(t, c -> SeedData.exec(c, inquiry("00000000-0000-4000-8000-0000000000bb", p[0], p[1], p[2], p[3],
                p[4].isEmpty() ? null : p[4]), t))).hasMessageContaining("23514");
    }

    @Test
    void theAnswererIsAnOperatorSubject() {
        String t = tenant("INQA");
        assertThatThrownBy(() -> DB.seed(t, c -> SeedData.exec(c, "INSERT INTO inquiry (tenant_id, inquiry_id, agent_id, category, message, status, "
                + "created_at, answered_at, answer, answered_by) VALUES (?, " + UUID_A + ", 'A0001', 'OTHER', 'm', 'ANSWERED', " + TS + ", " + TS
                + ", 'a', 'sub-agent')", t))).hasMessageContaining("23514");
    }

    private static String string(java.sql.Connection c, String sql, String t) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}

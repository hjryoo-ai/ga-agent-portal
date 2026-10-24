package com.ga.portal.domain.notify;

import com.ga.portal.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 포털 Phase 6 계획 ③·Q20·Q29: 종류마다 모양 하나, 경로는 모양에서만 나오고 화면 경로 넷의 형식이다, 키 조각은 퍼센트 부호화, 행 키는 이름 기반. */
class NotificationShapesTest {

    static final Map<Class<? extends NotificationParams>, NotificationParams> SAMPLE = Map.of(
            NotificationParams.Ledger.class, new NotificationParams.Ledger("L/1", "*********0001"),
            NotificationParams.Line.class, new NotificationParams.Line("C 1", "**", "202608"),
            NotificationParams.Deferral.class, new NotificationParams.Deferral("가나", "****", "202701"),
            NotificationParams.Policy.class, new NotificationParams.Policy("*0001", LocalDate.of(2027, 2, 10)),
            NotificationParams.Month.class, new NotificationParams.Month("202608"),
            NotificationParams.Inquiry.class, new NotificationParams.Inquiry("6f1c2b9e-0c1d-4e5f-8a9b-0c1d2e3f4a5b"));

    @Test
    void everyTypeHasOneShapeAndItsLinkIsAScreenPath() {
        Map<NotificationType, String> links = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            NotificationParams params = SAMPLE.get(NotificationShapes.paramsType(type));
            assertThat(NotificationShapes.fits(type, params)).as(type.name()).isTrue();
            NotificationShapes.link(params).ifPresent(l -> {
                assertThat(ScreenLinks.PATTERN.matcher(l).matches()).as(l).isTrue();
                links.put(type, l);
            });
        }
        assertThat(links).doesNotContainKeys(NotificationType.POLICY_CANCEL, NotificationType.POLICY_LAPSE, NotificationType.POLICY_PAYMENT_OVERDUE);
        assertThat(links).containsEntry(NotificationType.LIMIT_FULL, "/limits/L%2F1").containsEntry(NotificationType.CLAWBACK, "/lines/C%201")
                .containsEntry(NotificationType.DEFER_HELD, "/lines/%EA%B0%80%EB%82%98").containsEntry(NotificationType.PAYOUT, "/statements/202608")
                .containsEntry(NotificationType.INQUIRY_ANSWERED, "/inquiries/6f1c2b9e-0c1d-4e5f-8a9b-0c1d2e3f4a5b");
        assertThat(links).hasSize(NotificationType.values().length - 3);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"CALC-0001|CALC-0001", "a.b_c~d-e|a.b_c~d-e", "C/1|C%2F1", "50%|50%25", "x?y#z|x%3Fy%23z", "..|..", "가|%EA%B0%80"})
    void keysAreOnePathSegment(String key, String segment) {
        assertThat(ScreenLinks.segment(key)).isEqualTo(segment);
        assertThat(ScreenLinks.PATTERN.matcher(ScreenLinks.line(key)).matches()).isTrue();
    }

    @Test
    void theShapesRefuseWhatTheContractRefuses() {
        assertThatThrownBy(() -> new NotificationParams.Ledger("", "**")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationParams.Ledger("L1", "POL-2026-0001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationParams.Line("C1", "**", "202613")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationParams.Month("2026-08")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationParams.Inquiry("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationParams.Ledger("x".repeat(129), "**")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRecordIsKeyedByItsFactAndTakesItsSeverityFromItsType() {
        NotificationParams month = SAMPLE.get(NotificationParams.Month.class);
        Instant at = Instant.parse("2026-10-24T00:00:00Z");
        NotificationRecord a = NotificationRecord.fresh("GA01", "A-1001", NotificationType.PAYOUT, "PAYOUT:202608", month, at);
        NotificationRecord b = NotificationRecord.fresh("GA01", "A-1001", NotificationType.PAYOUT, "PAYOUT:202608", month, at.plusSeconds(60));
        assertThat(a.id()).isEqualTo(b.id());
        assertThat(NotificationRecord.fresh("GA02", "A-1001", NotificationType.PAYOUT, "PAYOUT:202608", month, at).id()).isNotEqualTo(a.id());
        assertThat(NotificationRecord.fresh("GA01", "A-1002", NotificationType.PAYOUT, "PAYOUT:202608", month, at).id()).isNotEqualTo(a.id());
        assertThat(a.link()).contains("/statements/202608");
        assertThat(a.severity().name()).isEqualTo("INFO");
        assertThatThrownBy(() -> NotificationRecord.fresh("GA01", "A-1001", NotificationType.LIMIT_FULL, "K", month, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRecord(a.id(), "A-1001", NotificationType.PAYOUT, "K", month, Optional.of("/statements/202609"), at,
                Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }
}

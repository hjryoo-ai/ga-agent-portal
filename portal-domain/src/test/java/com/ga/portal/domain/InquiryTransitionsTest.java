package com.ga.portal.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 포털 Phase 6 계획 ⑦: 답변은 OPEN에서만, 종료는 OPEN·ANSWERED에서만 — 세 상태 전수. 분류는 OTHER만 라인 없이. 한도 단계의 심각도 짝(계획 Q18). */
class InquiryTransitionsTest {

    @Test
    void answerOnlyFromOpenCloseFromOpenOrAnswered() {
        Set<InquiryStatus> answerable = EnumSet.noneOf(InquiryStatus.class);
        Set<InquiryStatus> closable = EnumSet.noneOf(InquiryStatus.class);
        for (InquiryStatus s : InquiryStatus.values()) {
            if (InquiryTransitions.canAnswer(s)) {
                answerable.add(s);
            }
            if (InquiryTransitions.canClose(s)) {
                closable.add(s);
            }
        }
        assertThat(answerable).containsExactly(InquiryStatus.OPEN);
        assertThat(closable).containsExactly(InquiryStatus.OPEN, InquiryStatus.ANSWERED);
    }

    @Test
    void onlyOtherNeedsNoLine() {
        assertThat(Arrays.stream(InquiryCategory.values()).filter(c -> !c.requiresCalcId())).containsExactly(InquiryCategory.OTHER);
    }

    @Test
    void limitStagesMapToTheDesignSeverities() {
        assertThat(NotificationType.LIMIT_WARN.severity()).isEqualTo(Severity.INFO);
        assertThat(NotificationType.LIMIT_CRIT.severity()).isEqualTo(Severity.WARN);
        assertThat(NotificationType.LIMIT_FULL.severity()).isEqualTo(Severity.CRIT);
        assertThat(NotificationType.INQUIRY_ANSWERED.severity()).isEqualTo(Severity.INFO);
    }
}

package com.ga.portal.projection;

import java.util.List;

/**
 * 프로젝터 적용 결과 — 알림 파생(Phase 6)과 지표의 입력.
 *
 * @param appliedTo 쓴 뷰 테이블 이름들(기록 프로젝터는 비어 있다)
 * @param drift     마감·지급 이벤트의 합계가 포털 합산과 달랐다(지시문 D-3 — 독이 아니라 그 달 DRIFT, 지표 {@code projection_drift_total})
 */
public record ProjectionResult(List<String> appliedTo, boolean drift) {

    public ProjectionResult {
        appliedTo = List.copyOf(appliedTo);
    }

    public static ProjectionResult of(List<String> appliedTo) {
        return new ProjectionResult(appliedTo, false);
    }

    public static ProjectionResult recordedOnly() {
        return new ProjectionResult(List.of(), false);
    }
}

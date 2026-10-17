package com.ga.portal.projection;

import java.util.List;

/**
 * 프로젝터 적용 결과. Phase 1은 적용 여부만 — Phase 2가 뷰 변경 내역(알림 파생의 입력)을 더한다.
 *
 * @param appliedTo 적용한 뷰의 이름들(Phase 1 기록 프로젝터는 비어 있다)
 */
public record ProjectionResult(List<String> appliedTo) {

    public ProjectionResult {
        appliedTo = List.copyOf(appliedTo);
    }

    public static ProjectionResult recordedOnly() {
        return new ProjectionResult(List.of());
    }
}

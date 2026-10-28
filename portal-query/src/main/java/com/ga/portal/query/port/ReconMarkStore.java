package com.ga.portal.query.port;

import java.util.Optional;

/**
 * 바인딩된 테넌트의 마지막 대사 회차 표식(포털 Phase 7) — 시각·결과·미해결 수·재구축 결과를 이은 불투명 문자열(값 없음), 회차가 없으면 비어 있다. 대사는
 * {@code seq}를 움직이지 않고 달의 {@code data_status}를 바꾸므로 조회 ETag의 재료다. 설정·커서 문장과 따로 한 문장(그 문장의 임계치 변환과 섞지 않는다 —
 * {@code ReconArithmeticScanTest}).
 */
@FunctionalInterface
public interface ReconMarkStore {

    Optional<String> last();
}

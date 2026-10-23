package com.ga.portal.query.port;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.view.AgentRow;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.DueMonth;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerItem;
import com.ga.portal.domain.view.LineDetail;
import com.ga.portal.domain.view.LinePage;
import com.ga.portal.domain.view.MonthDetail;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.domain.view.ReceivableRow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 설계사 본인의 뷰 조회(지시문 D-4·D-5). <b>모든 메서드가 {@code AgentId}를 받고</b>, 남의 키는 "없다"와 구별되지 않는다(빈 값). 바인딩된 테넌트의
 * {@link ReadTransaction} 안에서 부른다.
 */
public interface AgentQueryStore {

    Optional<AgentRow> profile(AgentId agent);

    /** {@code fromYm ≤ close_ym ≤ toYm}, 마감월 오름차순. */
    List<MonthRow> months(AgentId agent, String fromYm, String toYm);

    Optional<MonthRow> month(AgentId agent, String closeYm);

    /** 월 상세 — 그 달 행 + 지난달({@code previousYm}) 행의 값(포털 Phase 5 CR-1, 한 문장). */
    Optional<MonthDetail> monthDetail(AgentId agent, String closeYm, String previousYm);

    /** 그 달 라인의 {@code comm_type}별 정수 합(유형 이름순). */
    Map<String, Long> byType(AgentId agent, String closeYm);

    /** 그 달 라인 한 페이지. {@code policyNo}는 유스케이스가 {@code policyKey}를 그 설계사의 증권번호 집합에서 푼 원문(SQL에 키는 없다). */
    LinePage lines(AgentId agent, String closeYm, Optional<String> commType, Optional<String> policyNo, int page, int size);

    /** 그 달 라인의 증권번호 집합 — {@code policyKey} 필터를 풀 때만. */
    Set<String> linePolicies(AgentId agent, String closeYm);

    Optional<LineDetail> line(AgentId agent, String calcId);

    List<DeferralRow> deferralsOfLine(AgentId agent, String calcId);

    Optional<ReceivableRow> receivableOfLine(AgentId agent, String calcId);

    Optional<String> ledgerIdOfPolicy(AgentId agent, String policyNo);

    /** 원장 전부(원장 ID 순 — 숫자 문자열은 길이·사전순). */
    List<LedgerItem> ledgers(AgentId agent);

    Optional<LedgerItem> ledger(AgentId agent, String ledgerId);

    /** 그 원장의 타임라인({@code source_seq} 순) — 원장의 설계사로 조인해 남의 원장은 빈 목록. */
    List<HistoryRow> history(AgentId agent, String ledgerId);

    /** {@code fromYm ≤ due_ym ≤ toYm}의 분급 항목(지급월·일정 ID 순). */
    List<DeferralRow> deferrals(AgentId agent, String fromYm, String toYm, Optional<String> policyNo);

    /** 지급월별 원값 합. {@code policyNo}가 있으면 그 계약만(포털 Phase 5 Q15). */
    List<DueMonth> dueMonths(AgentId agent, String fromYm, String toYm, Optional<String> policyNo);

    /** 그 설계사의 분급 항목 증권번호 집합 — {@code policyKey} 필터를 풀 때만. */
    Set<String> deferralPolicies(AgentId agent);
}

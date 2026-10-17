package com.ga.portal.projection;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.projection.view.ViewStore;

import java.util.List;
import java.util.Objects;

/**
 * 뷰 프로젝터(지시문 Phase 2, 설계서 §3.4-1): 이벤트 13종을 자기 테이블로만 닫는다(계획 ② — 교차 참조는 읽기만). 금액은 계산하지 않는다 —
 * 정수 합산뿐이고 엔진 값(누적·세액·지급액)은 그대로 담는다. 순서에 기대지 않는다(D-6): 전기는 라인을 요구하지 않고 라인 상세가 calcId로 잇는다.
 * 거부는 {@link ProjectionViolation} — 적용기가 그 이벤트를 롤백하고 테넌트를 멈춘다. 대사 불일치는 거부가 아니다({@link ProjectionResult#drift()}).
 */
public final class ViewProjector implements Projector {

    private final ViewStore store;

    public ViewProjector(ViewStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public ProjectionResult apply(TenantId tenant, Envelope e) {
        return switch (e.type()) {
            case "AgentSnapshot" -> {
                AgentProjector.snapshot(store, e);
                yield ProjectionResult.of(List.of("agent_profile"));
            }
            case "CommissionCalculated" -> {
                EarningsProjector.calculated(store, e);
                yield ProjectionResult.of(List.of("earnings_line", "earnings_month", "policy_limit"));
            }
            case "AdjustmentPosted" -> {
                EarningsProjector.adjusted(store, e);
                yield ProjectionResult.of(List.of("earnings_line", "earnings_month"));
            }
            case "LimitLedgerOpened" -> {
                LimitProjector.opened(store, e);
                yield ProjectionResult.of(List.of("policy_limit"));
            }
            case "LimitLedgerPosted" -> {
                LimitProjector.posted(store, e);
                yield ProjectionResult.of(List.of("policy_limit", "policy_limit_history"));
            }
            case "LimitLedgerRebased" -> {
                LimitProjector.rebased(store, e);
                yield ProjectionResult.of(List.of("policy_limit", "policy_limit_history"));
            }
            case "DeferralScheduled" -> {
                DeferralProjector.scheduled(store, e);
                yield ProjectionResult.of(List.of("deferral_item"));
            }
            case "DeferralStatusChanged" -> {
                DeferralProjector.changed(store, e);
                yield ProjectionResult.of(List.of("deferral_item"));
            }
            case "ClawbackReceivableUpdated" -> {
                ClawbackProjector.updated(store, e);
                yield ProjectionResult.of(List.of("clawback_item"));
            }
            case "PolicyEventObserved" -> {
                FlagProjector.observed(store, e);
                yield ProjectionResult.of(List.of("policy_flag"));
            }
            case "StatementEstimated" -> new ProjectionResult(List.of("earnings_month"), StatementProjector.estimated(store, e));
            case "StatementConfirmed" -> new ProjectionResult(List.of("earnings_month", "recon_result"), StatementProjector.confirmed(store, e));
            case "PayoutCompleted" -> new ProjectionResult(List.of("earnings_month", "recon_result"), StatementProjector.paid(store, e));
            // 수신 검증이 계약의 13종 밖을 먼저 거른다 — 여기 오면 프로젝터와 계약이 어긋난 것이다
            default -> throw new IllegalStateException("no projector for type " + e.type());
        };
    }
}

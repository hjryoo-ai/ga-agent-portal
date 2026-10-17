package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;

/**
 * 인제스트 지표 포트(지시문 D-9, 설계서 §10). 구현은 {@code portal-app}(Micrometer, 태그 {@code tenant}). 시각은 주입된 시계에서 온다.
 */
public interface IngestMetrics {

    /** 결과: applied·skipped·failed. */
    void event(TenantId tenant, String result);

    void feedError(TenantId tenant);

    /** {@code headSeq − last_seq}와 그 지연의 초(따라잡았으면 0). */
    void lag(TenantId tenant, long lagSeq, long lagSeconds);

    void state(TenantId tenant, IngestState state);

    /** 마감·지급 이벤트의 합계가 포털 합산과 달랐다(Phase 2 지시문 D-3). */
    default void drift(TenantId tenant) {
    }

    IngestMetrics NONE = new IngestMetrics() {
        @Override
        public void event(TenantId tenant, String result) {
        }

        @Override
        public void feedError(TenantId tenant) {
        }

        @Override
        public void lag(TenantId tenant, long lagSeq, long lagSeconds) {
        }

        @Override
        public void state(TenantId tenant, IngestState state) {
        }
    };
}

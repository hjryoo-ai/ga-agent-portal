package com.ga.portal.recon;

import java.time.Duration;
import java.util.Objects;

/**
 * 테넌트의 대사 파라미터(설계서 §6.6, 계획 ⑥) — 값은 {@code tenant.params}, 없으면 앱 설정의 기본({@code portal.recon.defaults.*}). 코드에 값을 두지 않는다.
 *
 * @param reconHourKst     스케줄 회차를 여는 KST 시(0~23)
 * @param autoRebuild      DRIFT가 있을 때 자동 재구축을 부르는가({@code auto_rebuild_enabled})
 * @param caughtUpWithin   커서가 따라잡은 뒤 이 시간 안이어야 대사한다({@code recon_caught_up_within_seconds} — 계획 Q2)
 * @param callInterval     엔진 호출 사이 간격({@code recon_call_interval_ms})
 * @param maxCalls         회차 엔진 호출 예산({@code recon_max_calls})
 * @param maxDuration      회차 시간 예산({@code recon_max_seconds})
 */
public record ReconParams(int reconHourKst, boolean autoRebuild, Duration caughtUpWithin, Duration callInterval, int maxCalls, Duration maxDuration) {

    public ReconParams {
        Objects.requireNonNull(caughtUpWithin, "caughtUpWithin");
        Objects.requireNonNull(callInterval, "callInterval");
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (reconHourKst < 0 || reconHourKst > 23) {
            throw new IllegalArgumentException("reconHourKst must be 0..23: " + reconHourKst);
        }
        if (maxCalls < 0 || caughtUpWithin.isNegative() || callInterval.isNegative() || maxDuration.isNegative()) {
            throw new IllegalArgumentException("negative recon budget");
        }
    }
}

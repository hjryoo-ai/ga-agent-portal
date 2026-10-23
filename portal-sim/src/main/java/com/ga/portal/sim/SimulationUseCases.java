package com.ga.portal.sim;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.SimulationKind;
import com.ga.portal.domain.access.ApiAuthorization;
import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.NotFound;
import com.ga.portal.domain.access.UseCaseEntry;
import com.ga.portal.sim.EngineOutcomes.Failure;
import com.ga.portal.sim.EngineOutcomes.Outcome;
import com.ga.portal.sim.EngineOutcomes.Success;
import com.ga.portal.sim.port.EngineCredentials;
import com.ga.portal.sim.port.PolicyResolver;
import com.ga.portal.sim.port.SimFacts;
import com.ga.portal.sim.port.SimFacts.Facts;
import com.ga.portal.sim.port.SimMetrics;
import com.ga.portal.sim.port.SimTransactions;
import com.ga.portal.sim.port.SimulationLogStore;
import com.ga.portal.sim.port.SimulationLogStore.Settings;
import com.ga.portal.sim.port.SimulationLogStore.Window;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 시뮬레이션 두 진입점(포털 Phase 5 ①·⑤·⑥, 설계서 §4.3·§7). 엔진 결과를 <b>그대로</b> 옮길 뿐 금액을 만들거나 바꾸지 않는다(차이·합산·반올림 없음).
 * <ol>
 *   <li>역할 확인 → 요청 형식·계약 스키마(400 — 행 없음) → (계약 이벤트) {@code policyKey}를 그 설계사의 집합에서 푼다(404 — 행·엔진 호출 없음).</li>
 *   <li>트랜잭션 A: 설계사 잠금 → 설정·포털 사실 → 직전 60초 행 수 ≥ 한도면 429(행·감사 없음, 롤백) → {@code REQUESTED} 행.</li>
 *   <li>엔진 호출 — 트랜잭션 밖, 재시도 0. 토큰 파일이 없으면 부르지 않는다.</li>
 *   <li>트랜잭션 B: {@code SUCCEEDED}(검증된 엔진 응답·룰 스냅샷) 또는 {@code FAILED}(코드·엔진 상태, 본문 없음). B가 실패하면 행은 {@code REQUESTED}로 남는다.</li>
 * </ol>
 */
public final class SimulationUseCases {

    private static final System.Logger LOG = System.getLogger(SimulationUseCases.class.getName());
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SimulationLogStore log;
    private final SimTransactions transactions;
    private final PolicyResolver policies;
    private final EngineSimulationClient engine;
    private final EngineCredentials credentials;
    private final SimFacts facts;
    private final SimMetrics metrics;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final int defaultRatePerMinute;

    public SimulationUseCases(SimulationLogStore log, SimTransactions transactions, PolicyResolver policies, EngineSimulationClient engine,
                              EngineCredentials credentials, SimFacts facts, SimMetrics metrics, Clock clock, Supplier<UUID> ids,
                              int defaultRatePerMinute) {
        this.log = Objects.requireNonNull(log, "log");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        if (defaultRatePerMinute < 1) {
            throw new IllegalArgumentException("default rate must be positive");
        }
        this.defaultRatePerMinute = defaultRatePerMinute;
    }

    @UseCaseEntry(ApiAuthorization.SIM_NEW_CONTRACT)
    public byte[] newContract(Caller caller, Optional<String> contentType, byte[] body) {
        ApiAuthorization.require(caller, ApiAuthorization.SIM_NEW_CONTRACT);
        JsonNode request = SimRequests.read(contentType, body, SimRequests.NEW_CONTRACT);
        return run(caller, SimulationKind.NEW_CONTRACT, SimRequests.newContract(caller.agentId(), request));
    }

    @UseCaseEntry(ApiAuthorization.SIM_POLICY_EVENT)
    public byte[] policyEvent(Caller caller, Optional<String> contentType, byte[] body) {
        ApiAuthorization.require(caller, ApiAuthorization.SIM_POLICY_EVENT);
        JsonNode request = SimRequests.read(contentType, body, SimRequests.POLICY_EVENT);
        String policyNo = policies.policyNo(caller.tenant(), caller.agentId(), request.path("policyKey").asString())
                .orElseThrow(() -> new NotFound(DenyReason.MISSING));
        return run(caller, SimulationKind.POLICY_EVENT, SimRequests.policyEvent(caller.agentId(), policyNo, request));
    }

    private record Admitted(Settings settings, Facts facts) {
    }

    private byte[] run(Caller caller, SimulationKind kind, ObjectNode engineRequest) {
        TenantId tenant = caller.tenant();
        UUID simId = ids.get();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);   // timestamptz와 같은 정밀도 — 응답의 requestedAt = 행의 created_at
        String requestJson = JSON.writeValueAsString(engineRequest);
        Admitted admitted;
        try {
            admitted = transactions.write(() -> {
                log.lock(caller.agentId());
                Settings settings = log.settings();
                Facts current = facts.current();
                Window window = log.window(caller.agentId(), now.minus(RateLimitRule.WINDOW));
                RateLimitRule.retryAfter(window.count(), window.oldest(), settings.ratePerMinute().orElse(defaultRatePerMinute), now)
                        .ifPresent(seconds -> {
                            throw new RateLimited(seconds);
                        });
                log.insertRequested(simId, caller.agentId(), kind, requestJson, now);
                return new Admitted(settings, current);
            });
        } catch (RateLimited e) {
            metrics.rateLimited(tenant);
            throw e;
        }

        Outcome outcome = credentials.token(tenant)
                .map(token -> EngineOutcomes.classify(kind, engine.call(admitted.settings().engineBaseUrl(), kind, token, requestJson.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8))))
                .orElseGet(EngineOutcomes::tokenMissing);

        switch (outcome) {
            case Success s -> {
                SimResponses.Mapped mapped = SimResponses.portal(kind, admitted.facts(), simId, now, s.result());
                transactions.write(() -> {
                    log.succeeded(simId, caller.agentId(), JSON.writeValueAsString(s.result()), s.ruleSnapshotId(), s.status());
                    return null;
                });
                metrics.request(tenant, "OK");
                if (mapped.droppedFields() > 0) {
                    LOG.log(System.Logger.Level.INFO, "SIM_UNKNOWN_FIELDS tenant=" + tenant.value() + " kind=" + kind + " dropped=" + mapped.droppedFields());
                }
                return mapped.body();
            }
            case Failure f -> {
                transactions.write(() -> {
                    log.failed(simId, caller.agentId(), f.logCode(), f.engineStatus());
                    return null;
                });
                metrics.request(tenant, f.code().name());
                LOG.log(System.Logger.Level.WARNING, "SIM_FAILED tenant=" + tenant.value() + " kind=" + kind + " code=" + f.logCode()
                        + " engineStatus=" + f.engineStatus().map(String::valueOf).orElse("-") + " simId=" + simId);
                throw new SimulationFailed(f.code());
            }
        }
    }
}

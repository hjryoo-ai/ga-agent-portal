package com.ga.portal.demo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 데모 테넌트의 엔진 데이터(설계서 부록 B v1.11 — 엔진 {@code DemoContractTest}가 낸 묶음, 포털 사본
 * {@code contracts/shared/ga-commission-engine/samples/demo/}). 포털은 이 바이트를 고르고 내보낼 뿐 아무것도 계산하지 않는다(규칙 1).
 *
 * @param tenantId 피드 응답의 {@code tenantId}(엔진은 테넌트별 인스턴스 — Envelope에는 테넌트가 없다)
 * @param events 스트림의 Envelope(파일 순서 — seq 오름차순)
 * @param statements 명세 조회 키({@code agentId|ym|mode}) → 엔진 응답
 * @param simulations 시뮬레이션 샘플(경로 {@code new-contract}·{@code policy-event}, 요청 값, 엔진 응답)
 */
public record DemoData(String tenantId, List<JsonNode> events, Map<String, JsonNode> statements, List<Simulation> simulations) {

    static final JsonMapper JSON = JsonMapper.builder().build();

    /** 시뮬레이션 샘플 하나. */
    public record Simulation(String path, JsonNode request, JsonNode response) {
    }

    public DemoData {
        Objects.requireNonNull(tenantId, "tenantId");
        events = List.copyOf(events);
        statements = Map.copyOf(statements);
        simulations = List.copyOf(simulations);
    }

    /** 묶음 디렉터리({@code demo_ga.jsonl}·{@code statements.json}·{@code simulations.json})를 읽는다. */
    public static DemoData load(Path dir, String tenantId) {
        try {
            List<JsonNode> events = new ArrayList<>();
            for (String line : Files.readAllLines(dir.resolve("demo_ga.jsonl"), StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    events.add(JSON.readTree(line));
                }
            }
            Map<String, JsonNode> statements = new HashMap<>();
            for (JsonNode s : JSON.readTree(dir.resolve("statements.json").toFile())) {
                statements.put(key(s.path("agentId").asString(), s.path("ym").asString(), s.path("mode").asString()), s.path("response"));
            }
            List<Simulation> simulations = new ArrayList<>();
            for (JsonNode s : JSON.readTree(dir.resolve("simulations.json").toFile())) {
                simulations.add(new Simulation(s.path("path").asString(), s.path("request"), s.path("response")));
            }
            return new DemoData(tenantId, events, statements, simulations);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String key(String agentId, String ym, String mode) {
        return agentId + "|" + ym + "|" + mode;
    }

    Optional<JsonNode> statement(String agentId, String ym, String mode) {
        return Optional.ofNullable(statements.get(key(agentId, ym, mode)));
    }

    /** 요청과 <b>값이 같은</b> 샘플(객체 키 순서는 보지 않는다 — 포털 요청은 엔진 계약의 필드 순서지만 그것에 기대지 않는다). */
    Optional<JsonNode> simulation(String path, JsonNode request) {
        return simulations.stream().filter(s -> s.path().equals(path) && s.request().equals(request)).map(Simulation::response).findFirst();
    }
}

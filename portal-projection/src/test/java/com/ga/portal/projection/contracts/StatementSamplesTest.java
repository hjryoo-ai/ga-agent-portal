package com.ga.portal.projection.contracts;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 ⓪·C1: 엔진 명세 샘플 사본({@code contracts/shared/ga-commission-engine/samples/statements/<엔진 상태>/} — 엔진
 * {@code StatementContractTest}가 쓴 바이트, {@code UPSTREAM}의 병합 커밋과 {@code verifyUpstreamContract}가 대조)이 포털 계약을 지킨다.
 * <ul>
 *   <li>묶음은 셋(계획 P-2), 묶음마다 파일 집합 = {@code index.json}의 행 × 요청·응답 + {@code index.json}(닫힌 집합).</li>
 *   <li>요청 파일 = 그 행의 {@code agentId}·{@code ym}·{@code mode}(스텁이 이것으로 대조한다).</li>
 *   <li>200 = 모드의 payload 스키마({@code getStatement} — ESTIMATE → {@code StatementEstimated}, CONFIRMED → {@code StatementConfirmed}),
 *       {@code agentId}·{@code closeYm}이 요청과 같다. 그 밖 = {@code Problem}이고 코드가 계약의 그 상태 코드 중 하나다.</li>
 *   <li>A-1 묶음은 포털 A-1 스트림 사본과 같은 사실이다: {@code 202702 ESTIMATE} = seq 11 payload(계획 P-13 — {@code basisSeq} 10),
 *       {@code 202608 CONFIRMED}의 세 금액 = seq 6 payload.</li>
 * </ul>
 */
class StatementSamplesTest {

    private static final String PORTAL = "https://ga.example/portal/contracts/";
    private static final Path CONTRACTS = Path.of(System.getProperty("ga.repoRoot"), "contracts");
    private static final Path ROOT = CONTRACTS.resolve("shared/ga-commission-engine/samples/statements");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    /** {@code getStatement} 오류 상태 → 계약의 코드(engine-internal.openapi.yaml). */
    private static final Map<Integer, Set<String>> CODES = Map.of(
            400, Set.of("INVALID_REQUEST"),
            404, Set.of("AGENT_NOT_FOUND", "STATEMENT_NOT_CONFIRMED"),
            422, Set.of("MONTH_CLOSED"));

    private static SchemaRegistry registry;

    @BeforeAll
    static void registry() {
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, b -> b.schemas(iri -> {
            if (iri.startsWith(PORTAL)) {
                Path file = CONTRACTS.resolve(iri.substring(PORTAL.length()));
                String text = read(file);
                return file.toString().endsWith(".yaml") ? JSON.writeValueAsString(YAML.readTree(text)) : text;
            }
            return null;
        }));
    }

    private static Schema schema(String iri) {
        return registry.getSchema(SchemaLocation.of(iri));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> list(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theBundlesAreTheThreeEngineStates() {
        assertThat(list(ROOT)).containsExactly("a1", "estimate-cycle-01", "estimate-cycle-05");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a1", "estimate-cycle-01", "estimate-cycle-05"})
    void everySampleKeepsTheContract(String state) {
        Path dir = ROOT.resolve(state);
        JsonNode index = JSON.readTree(read(dir.resolve("index.json")));
        Set<String> expected = new TreeSet<>(Set.of("index.json"));
        for (JsonNode row : index) {
            String name = row.path("name").asString();
            expected.add(name + ".request.json");
            expected.add(name + ".response.json");
            JsonNode request = JSON.readTree(read(dir.resolve(name + ".request.json")));
            assertThat(request.propertyNames()).as(name).containsExactlyInAnyOrder("agentId", "ym", "mode");
            assertThat(request.path("agentId")).as(name).isEqualTo(row.path("agentId"));
            assertThat(request.path("ym")).as(name).isEqualTo(row.path("ym"));
            assertThat(request.path("mode")).as(name).isEqualTo(row.path("mode"));

            JsonNode response = JSON.readTree(read(dir.resolve(name + ".response.json")));
            int status = row.path("status").asInt();
            if (status == 200) {
                String type = request.path("mode").asString().equals("ESTIMATE") ? "StatementEstimated" : "StatementConfirmed";
                assertThat(schema(PORTAL + "events/v1/payloads/" + type + ".v1.schema.json").validate(response)).as(name).isEmpty();
                assertThat(response.path("agentId")).as(name).isEqualTo(request.path("agentId"));
                assertThat(response.path("closeYm")).as(name).isEqualTo(request.path("ym"));
            } else {
                assertThat(schema(PORTAL + "api/v1/engine-internal.openapi.yaml#/components/schemas/Problem").validate(response)).as(name).isEmpty();
                assertThat(CODES.get(status)).as(name + " " + status).contains(response.path("code").asString());
            }
        }
        assertThat(list(dir)).as("closed file set of " + state).isEqualTo(expected);
    }

    @Test
    void theA1BundleIsTheSameFactAsThePortalA1Stream() {
        JsonNode seq6 = null;
        JsonNode seq11 = null;
        for (String line : read(CONTRACTS.resolve("events/v1/samples/a1/a1_portal_150000_e2.jsonl")).split("\n")) {
            if (!line.isBlank()) {
                JsonNode e = JSON.readTree(line);
                if (e.path("seq").asLong() == 6) {
                    seq6 = e;
                } else if (e.path("seq").asLong() == 11) {
                    seq11 = e;
                }
            }
        }
        assertThat(seq6.path("type").asString()).isEqualTo("StatementConfirmed");
        assertThat(seq11.path("type").asString()).isEqualTo("StatementEstimated");
        JsonNode estimate = JSON.readTree(read(ROOT.resolve("a1/202702-estimate.response.json")));
        assertThat(estimate).isEqualTo(seq11.path("payload"));
        assertThat(estimate.path("basisSeq").asLong()).isEqualTo(10);
        JsonNode confirmed = JSON.readTree(read(ROOT.resolve("a1/202608-confirmed.response.json")));
        for (String f : new String[] {"gross", "clawbackOffset", "adjustment", "closedAt"}) {
            assertThat(confirmed.path(f)).as(f).isEqualTo(seq6.path("payload").path(f));
        }
    }
}

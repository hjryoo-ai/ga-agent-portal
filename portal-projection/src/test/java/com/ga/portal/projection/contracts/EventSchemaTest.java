package com.ga.portal.projection.contracts;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C7(지시문 v1.2 §5): 포털 계약(contracts/events/v1·api/v1)과 공유 핵심 사본(contracts/shared/ga-disclosure).
 * <ul>
 *   <li>샘플 17건(v1.4 — 엔진 A-1 스트림 11줄 + 단건 6)이 13종을 덮고 전부 통과, 필수 필드를 하나씩 뺀 변형은 전부 실패.</li>
 *   <li>포털 Envelope ⊇ 핵심: 핵심이 거부하는 변형을 포털 Envelope도 거부하고, 포털 Envelope의 allOf 첫 항이 사본을 가리킨다.</li>
 *   <li>type ↔ payload, type ↔ aggregate.kind 짝(Q4·Q5), 열거 밖 type·version은 거부(else: false), 모르는 필드는 허용(§4.5).</li>
 *   <li>요율·배수는 소수 문자열(Q3) — JSON number는 거부. contracts/ 전 파일 format 0. OpenAPI 피드의 정수 커서 규약.</li>
 *   <li>v1.1(Phase 1 지시문 §0): 엔진에 없는 데이터는 선택(K1~K4), PayoutCompleted 확정값 선택 필드(K5), 금액 정의 문장(K6·K7),
 *       피드 응답 필수 tenantId·422 Problem·limit 기본 200(K8), PolicyEventObserved 열거 설명(K9).</li>
 *   <li>v1.2(Phase 2 지시문 §0): 피드 400(M1), 분급 사유·채권 상태 닫힌 열거와 채권 금액 0 이상(M2·M3), 샘플 손질(M4),
 *       샘플 디렉터리 a1/·contract-only/·standalone/(M5).</li>
 *   <li>v1.3(Phase 2 승인 §3): a1/ = 엔진 E2 ⓪a의 A-1 스트림 바이트 그대로, 동기 API R1~R8.</li>
 *   <li>v1.4.1(포털 Phase 8 ⓪a, DD-19): 표기만 — StatementEstimated.gross의 미리보기 문장, 설명문을 뺀 트리는 1.4.0과 같다.</li>
 * </ul>
 * 스키마 IRI는 저장소 파일로만 풀린다(원격 조회 없음): {@code https://ga.example/portal/contracts/} → {@code contracts/},
 * {@code https://ga.example/contracts/}(ga-disclosure의 IRI) → {@code contracts/shared/ga-disclosure/}.
 */
class EventSchemaTest {

    private static final String PORTAL = "https://ga.example/portal/contracts/";
    private static final String DISCLOSURE = "https://ga.example/contracts/";
    private static final String CORE_ID = DISCLOSURE + "events/v1/envelope-core.schema.json";
    private static final String ENVELOPE = PORTAL + "events/v1/envelope.schema.json";
    private static final Path CONTRACTS = Path.of(System.getProperty("ga.repoRoot"), "contracts");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    static final List<String> EVENT_TYPES = List.of(
            "AgentSnapshot", "CommissionCalculated", "AdjustmentPosted", "LimitLedgerOpened", "LimitLedgerPosted",
            "LimitLedgerRebased", "DeferralScheduled", "DeferralStatusChanged", "ClawbackReceivableUpdated",
            "PolicyEventObserved", "StatementEstimated", "StatementConfirmed", "PayoutCompleted");

    static final Map<String, String> KINDS = Map.ofEntries(
            Map.entry("AgentSnapshot", "AGENT"), Map.entry("CommissionCalculated", "COMM_CALC"),
            Map.entry("AdjustmentPosted", "ADJUSTMENT"), Map.entry("LimitLedgerOpened", "LIMIT_LEDGER"),
            Map.entry("LimitLedgerPosted", "LIMIT_LEDGER"), Map.entry("LimitLedgerRebased", "LIMIT_LEDGER"),
            Map.entry("DeferralScheduled", "DEFERRAL_SCHEDULE"), Map.entry("DeferralStatusChanged", "DEFERRAL_SCHEDULE"),
            Map.entry("ClawbackReceivableUpdated", "CLAWBACK_RECEIVABLE"), Map.entry("PolicyEventObserved", "POLICY"),
            Map.entry("StatementEstimated", "STATEMENT"), Map.entry("StatementConfirmed", "STATEMENT"), Map.entry("PayoutCompleted", "PAYOUT"));

    private static SchemaRegistry registry;

    @BeforeAll
    static void registry() {
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, b -> b.schemas(iri -> {
            if (iri.startsWith(PORTAL)) {
                return schemaText(CONTRACTS.resolve(iri.substring(PORTAL.length())));
            }
            if (iri.startsWith(DISCLOSURE)) {
                return schemaText(CONTRACTS.resolve("shared/ga-disclosure").resolve(iri.substring(DISCLOSURE.length())));
            }
            return null;
        }));
    }

    private static Schema schema(String iri) {
        return registry.getSchema(SchemaLocation.of(iri));
    }

    private static String schemaText(Path file) {
        String text = readString(file);
        return file.toString().endsWith(".yaml") ? JSON.writeValueAsString(YAML.readTree(text)) : text;
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectNode read(Path file) {
        return (ObjectNode) JSON.readTree(readString(file));
    }

    /** 샘플 하나 — 이름은 단건 파일 이름 또는 {@code a1#<seq>}(엔진 A-1 스트림 {@code a1/a1_portal_150000_e2.jsonl}의 그 seq 줄). */
    record Sample(String name, ObjectNode doc) {
    }

    private static final Path A1_STREAM = CONTRACTS.resolve("events/v1/samples/a1/a1_portal_150000_e2.jsonl");

    /** 샘플 전부(v1.4): 엔진 A-1 스트림 11줄(파일 순서) 뒤에 {@code contract-only/}·{@code standalone/}의 단건 6개(파일 이름 순). */
    static List<Sample> samples() {
        List<Sample> out = new ArrayList<>();
        for (String line : readString(A1_STREAM).split("\n")) {
            if (!line.isBlank()) {
                ObjectNode doc = (ObjectNode) JSON.readTree(line);
                out.add(new Sample("a1#" + doc.path("seq").asLong(), doc));
            }
        }
        try (Stream<Path> files = Stream.concat(Files.list(CONTRACTS.resolve("events/v1/samples/contract-only")),
                Files.list(CONTRACTS.resolve("events/v1/samples/standalone")))) {
            files.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> out.add(new Sample(p.getFileName().toString(), read(p))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** 이름이 {@code name}인 샘플의 복사본. */
    static ObjectNode sample(String name) {
        return samples().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow().doc().deepCopy();
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> namesIn(String dir) {
        try (Stream<Path> files = Files.list(CONTRACTS.resolve("events/v1/samples").resolve(dir))) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Stream<Arguments> sampleFiles() {
        return samples().stream().map(s -> Arguments.argumentSet(s.name(), s.doc()));
    }

    private static ObjectNode payloadSchema(String type) {
        return read(CONTRACTS.resolve("events/v1/payloads/" + type + ".v1.schema.json"));
    }

    // ------------------------------------------------------------------ 샘플

    @Test
    void samplesCoverThe13TypesAndA1IsTheEngineStream() {
        List<ObjectNode> all = samples().stream().map(Sample::doc).toList();
        assertThat(all).hasSize(17);
        assertThat(all.stream().map(s -> s.path("type").asString()).distinct()).containsExactlyInAnyOrderElementsOf(EVENT_TYPES);
        List<ObjectNode> a1 = all.subList(0, 10);
        assertThat(a1.stream().map(s -> s.path("seq").asLong())).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(a1.stream().map(s -> s.path("type").asString())).containsExactly("LimitLedgerOpened", "CommissionCalculated",
                "CommissionCalculated", "LimitLedgerPosted", "LimitLedgerPosted", "StatementConfirmed", "PayoutCompleted", "PolicyEventObserved",
                "CommissionCalculated", "LimitLedgerPosted");
        // A-1(월납 150,000 — 엔진 E2 ⓪a): 한도 1,800,000 = FY 945,000 + 시책 855,000(삭감 145,000 뒤) — amount는 삭감 뒤 지급분(I1)
        assertThat(a1.get(0).path("payload").path("limitAmount").asLong()).isEqualTo(1_800_000L);
        assertThat(a1.get(1).path("payload").path("amount").asLong()).isEqualTo(945_000L);
        assertThat(a1.get(2).path("payload").path("amount").asLong()).isEqualTo(855_000L);
        assertThat(a1.get(2).path("payload").path("limitCutAmt").asLong()).isEqualTo(145_000L);
        assertThat(a1.get(4).path("payload").path("accumAfter").asLong()).isEqualTo(1_800_000L);
        assertThat(a1.get(6).path("payload").path("withholdingTax").asLong()).isEqualTo(59_400L);
        assertThat(a1.get(6).path("payload").path("paidAmount").asLong()).isEqualTo(1_740_600L);
        assertThat(a1.get(8).path("payload").path("amount").asLong()).isEqualTo(-720_000L);
        assertThat(a1.get(9).path("payload").path("accumAfter").asLong()).isEqualTo(1_080_000L);
    }

    /**
     * M5(v1.2)·v1.3·v1.4: {@code a1/}는 엔진 E2 ⓪가 낸 A-1 스트림 둘째 판 하나(바이트 그대로 — SHA-256 고정, 첫 판이 앞부분),
     * {@code contract-only/}는 엔진이 내지 않는 형태(LIMIT_DEFER 스케줄과 그 취소 — StatementEstimated는 v1.4에서 a1 seq 11의 실 샘플로),
     * {@code standalone/}은 엔진이 내지만 A-1 밖인 단건 예시.
     */
    @Test
    void samplesAreSplitIntoTheEngineA1TheContractOnlyAndTheStandaloneDirectories() {
        assertThat(namesIn("a1")).containsExactly("a1_portal_150000_e2.jsonl");
        // 엔진 ga-commission-engine@b6b65df(e2 = 4680ee1) commission-outbox/src/test/resources/outbox-golden/a1_portal_150000_e2.jsonl
        assertThat(sha256(A1_STREAM)).isEqualTo("e9a03d37c513e591e7623d95bce6bc49ef2269e3c1f1336d369be2cc1eeb751a");
        assertThat(namesIn("contract-only")).containsExactly("06-DeferralScheduled.json", "13-DeferralStatusChanged.json");
        assertThat(namesIn("standalone")).containsExactly("14-ClawbackReceivableUpdated.json", "15-AdjustmentPosted.json",
                "16-AgentSnapshot.json", "17-LimitLedgerRebased.json");
        try (Stream<Path> top = Files.list(CONTRACTS.resolve("events/v1/samples"))) {
            assertThat(top.map(p -> p.getFileName().toString())).as("nothing outside the three directories")
                    .containsExactlyInAnyOrder("a1", "contract-only", "standalone");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(sample("06-DeferralScheduled.json").path("payload").path("reason").asString()).isEqualTo("LIMIT_DEFER");
    }

    /** M4(v1.2): 엔진이 마감 시점에 갖지 않는 값·어휘를 샘플에서 뺐다. */
    @Test
    void samplesCarryOnlyWhatTheEngineHasAtThatPoint() {
        JsonNode confirmed = sample("a1#6").path("payload");
        assertThat(confirmed.has("withholdingTax")).isFalse();
        assertThat(confirmed.has("net")).isFalse();
        assertThat(sample("13-DeferralStatusChanged.json").path("payload").path("reason").asString()).isEqualTo("POLICY_TERMINATED");
        JsonNode receivable = sample("14-ClawbackReceivableUpdated.json").path("payload");
        assertThat(receivable.path("amount").asLong()).isPositive();
        assertThat(receivable.path("remaining").asLong()).isBetween(1L, receivable.path("amount").asLong() - 1);
        assertThat(receivable.path("status").asString()).isEqualTo("OFFSET");
        JsonNode agent = sample("16-AgentSnapshot.json").path("payload");
        assertThat(agent.has("role")).isFalse();
        assertThat(agent.has("nameMasked")).isFalse();
    }

    /** N6(v1.4 — 좁힘): {@code AgentSnapshot.status}는 엔진 어휘({@code AgentSnapshotPublisher}) {@code ACTIVE}/{@code DISMISSED}로 닫힌 열거. */
    @Test
    void agentSnapshotStatusIsTheEnginesClosedVocabulary() {
        assertThat(payloadSchema("AgentSnapshot").path("properties").path("status").path("enum")).extracting(JsonNode::asString)
                .containsExactly("ACTIVE", "DISMISSED");
        for (String status : List.of("ACTIVE", "DISMISSED")) {
            ObjectNode doc = sample("16-AgentSnapshot.json");
            ((ObjectNode) doc.path("payload")).put("status", status);
            assertThat(schema(ENVELOPE).validate(doc)).as(status).isEmpty();
        }
        for (String status : List.of("RETIRED", "active", "")) {
            ObjectNode doc = sample("16-AgentSnapshot.json");
            ((ObjectNode) doc.path("payload")).put("status", status);
            assertThat(schema(ENVELOPE).validate(doc)).as("status %s", status).isNotEmpty();
        }
    }

    /** M2·M3(v1.2 — 좁힘): 분급 전이 사유·채권 상태는 엔진 어휘로 닫힌 열거, 채권 금액은 0 이상. */
    @Test
    void deferralReasonAndReceivableStatusAreTheEnginesClosedVocabularies() {
        assertThat(payloadSchema("DeferralStatusChanged").path("properties").path("reason").path("enum")).extracting(JsonNode::asString)
                .containsExactly("DUE_RELEASED", "POLICY_NOT_INFORCE", "POLICY_TERMINATED", "SOURCE_REVERSED");
        assertThat(payloadSchema("ClawbackReceivableUpdated").path("properties").path("status").path("enum")).extracting(JsonNode::asString)
                .containsExactly("OPEN", "OFFSET", "CLOSED", "WRITTEN_OFF");
        for (String reason : List.of("DUE_RELEASED", "POLICY_NOT_INFORCE", "POLICY_TERMINATED", "SOURCE_REVERSED")) {
            ObjectNode doc = sample("13-DeferralStatusChanged.json");
            ((ObjectNode) doc.path("payload")).put("reason", reason);
            assertThat(schema(ENVELOPE).validate(doc)).as(reason).isEmpty();
        }
        for (String reason : List.of("POLICY_CANCELLED", "policy_terminated", "")) {
            ObjectNode doc = sample("13-DeferralStatusChanged.json");
            ((ObjectNode) doc.path("payload")).put("reason", reason);
            assertThat(schema(ENVELOPE).validate(doc)).as("reason %s", reason).isNotEmpty();
        }
        for (String status : List.of("PARTIALLY_OFFSET", "offset", "")) {
            ObjectNode doc = sample("14-ClawbackReceivableUpdated.json");
            ((ObjectNode) doc.path("payload")).put("status", status);
            assertThat(schema(ENVELOPE).validate(doc)).as("status %s", status).isNotEmpty();
        }
        for (String field : List.of("amount", "remaining")) {
            ObjectNode negative = sample("14-ClawbackReceivableUpdated.json");
            ((ObjectNode) negative.path("payload")).put(field, -1);
            assertThat(schema(ENVELOPE).validate(negative)).as("%s −1", field).isNotEmpty();
            ObjectNode zero = sample("14-ClawbackReceivableUpdated.json");
            ((ObjectNode) zero.path("payload")).put(field, 0);
            assertThat(schema(ENVELOPE).validate(zero)).as("%s 0", field).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("sampleFiles")
    void everySamplePassesTheEnvelopeTheCoreAndItsPayloadSchema(ObjectNode given) {
        ObjectNode sample = given.deepCopy();
        String type = sample.path("type").asString();
        assertThat(schema(ENVELOPE).validate(sample)).isEmpty();
        assertThat(schema(CORE_ID).validate(sample)).isEmpty();
        assertThat(schema(PORTAL + "events/v1/payloads/" + type + ".v1.schema.json").validate(sample.path("payload"))).isEmpty();
        assertThat(sample.path("aggregate").path("kind").asString()).isEqualTo(KINDS.get(type));
    }

    /** 각 샘플에서 필수 필드(Envelope 일곱 개 + payload의 required, 안쪽 객체의 required까지)를 하나씩 뺀 변형은 전부 실패한다. */
    @ParameterizedTest
    @MethodSource("sampleFiles")
    void removingAnyRequiredFieldFails(ObjectNode given) {
        ObjectNode sample = given.deepCopy();
        Schema envelope = schema(ENVELOPE);
        for (String field : List.of("eventId", "seq", "type", "version", "occurredAt", "aggregate", "payload")) {
            ObjectNode missing = sample.deepCopy();
            missing.remove(field);
            assertThat(envelope.validate(missing)).as("without envelope.%s", field).isNotEmpty();
        }
        List<List<String>> paths = new ArrayList<>();
        requiredPaths(payloadSchema(sample.path("type").asString()), List.of(), paths);
        assertThat(paths).isNotEmpty();
        for (List<String> path : paths) {
            ObjectNode missing = sample.deepCopy();
            ObjectNode parent = (ObjectNode) missing.path("payload");
            for (String step : path.subList(0, path.size() - 1)) {
                parent = (ObjectNode) parent.path(step);
            }
            if (!parent.has(path.getLast())) {
                continue;   // 샘플에 없는 선택 필드의 하위 required는 없다
            }
            parent.remove(path.getLast());
            assertThat(envelope.validate(missing)).as("without payload.%s", String.join(".", path)).isNotEmpty();
        }
    }

    private static void requiredPaths(JsonNode schema, List<String> prefix, List<List<String>> out) {
        for (JsonNode r : schema.path("required")) {
            List<String> path = new ArrayList<>(prefix);
            path.add(r.asString());
            out.add(path);
        }
        schema.path("properties").properties().forEach(e -> {
            if (e.getValue().has("required")) {
                List<String> path = new ArrayList<>(prefix);
                path.add(e.getKey());
                requiredPaths(e.getValue(), path, out);
            }
        });
    }

    // ------------------------------------------------------------------ 포털 Envelope ⊇ 핵심

    @Test
    void thePortalEnvelopeIncludesTheCoreCopyAsItsFirstAllOf() {
        ObjectNode envelope = read(CONTRACTS.resolve("events/v1/envelope.schema.json"));
        assertThat(envelope.path("allOf").get(0).path("$ref").asString()).isEqualTo(CORE_ID);
        assertThat(read(CONTRACTS.resolve("shared/ga-disclosure/events/v1/envelope-core.schema.json")).path("$id").asString()).isEqualTo(CORE_ID);
    }

    static Stream<Arguments> coreViolations() {
        return Stream.of(
                Arguments.argumentSet("eventId not a uuid", (Mutation) s -> s.put("eventId", "not-a-uuid")),
                Arguments.argumentSet("eventId upper case", (Mutation) s -> s.put("eventId", s.path("eventId").asString().toUpperCase(java.util.Locale.ROOT))),
                Arguments.argumentSet("seq 0", (Mutation) s -> s.put("seq", 0)),
                Arguments.argumentSet("seq string", (Mutation) s -> s.put("seq", "1")),
                Arguments.argumentSet("version 0", (Mutation) s -> s.put("version", 0)),
                Arguments.argumentSet("type lower case", (Mutation) s -> s.put("type", "commissionCalculated")),
                Arguments.argumentSet("occurredAt without offset", (Mutation) s -> s.put("occurredAt", "2026-08-20T10:15:30")),
                Arguments.argumentSet("occurredAt date only", (Mutation) s -> s.put("occurredAt", "2026-08-20")),
                Arguments.argumentSet("aggregate.kind lower case", (Mutation) s -> ((ObjectNode) s.path("aggregate")).put("kind", "comm_calc")),
                Arguments.argumentSet("aggregate.id empty", (Mutation) s -> ((ObjectNode) s.path("aggregate")).put("id", "")),
                Arguments.argumentSet("aggregate without kind", (Mutation) s -> ((ObjectNode) s.path("aggregate")).remove("kind")),
                Arguments.argumentSet("payload not an object", (Mutation) s -> s.put("payload", "x")));
    }

    @FunctionalInterface
    interface Mutation {
        void apply(ObjectNode sample);
    }

    @ParameterizedTest
    @MethodSource("coreViolations")
    void everythingTheCoreRejectsThePortalEnvelopeRejects(Mutation mutation) {
        // 엔진 eventId는 순차라 대부분 숫자뿐이다 — "대문자" 변형이 실제로 바꾸도록 16진 문자가 있는 줄(seq 10 …00a)을 쓴다
        ObjectNode doc = sample("a1#10");
        assertThat(doc.path("eventId").asString()).matches(".*[a-f].*");
        mutation.apply(doc);
        assertThat(schema(CORE_ID).validate(doc)).as("core").isNotEmpty();
        assertThat(schema(ENVELOPE).validate(doc)).as("portal envelope").isNotEmpty();
    }

    // ------------------------------------------------------------------ 짝·열거·열린 필드

    @Test
    void aPayloadOfAnotherTypeIsRejected() {
        Map<String, ObjectNode> byType = new TreeMap<>();
        samples().stream().map(Sample::doc).forEach(s -> byType.putIfAbsent(s.path("type").asString(), s));
        for (int i = 0; i < EVENT_TYPES.size(); i++) {
            ObjectNode doc = byType.get(EVENT_TYPES.get(i)).deepCopy();
            JsonNode foreign = byType.get(EVENT_TYPES.get((i + 1) % EVENT_TYPES.size())).path("payload");
            doc.set("payload", foreign.deepCopy());
            assertThat(schema(ENVELOPE).validate(doc)).as("%s carrying a %s payload", EVENT_TYPES.get(i), EVENT_TYPES.get((i + 1) % EVENT_TYPES.size()))
                    .isNotEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("sampleFiles")
    void aggregateKindMustBeTheOnePairedWithTheType(ObjectNode given) {
        ObjectNode sample = given.deepCopy();
        Set<String> others = new LinkedHashSet<>(KINDS.values());
        others.remove(sample.path("aggregate").path("kind").asString());
        for (String kind : others) {
            ObjectNode doc = sample.deepCopy();
            ((ObjectNode) doc.path("aggregate")).put("kind", kind);
            assertThat(schema(ENVELOPE).validate(doc)).as("%s with kind %s", sample.path("type").asString(), kind).isNotEmpty();
        }
    }

    @Test
    void unknownTypeOrVersionIsRejectedEvenWhenTheCoreAcceptsIt() {
        ObjectNode unknown = sample("a1#7");
        unknown.put("type", "BonusGranted");
        assertThat(schema(CORE_ID).validate(unknown)).isEmpty();
        assertThat(schema(ENVELOPE).validate(unknown)).isNotEmpty();

        ObjectNode v2 = sample("a1#7");
        v2.put("version", 2);
        assertThat(schema(CORE_ID).validate(v2)).isEmpty();
        assertThat(schema(ENVELOPE).validate(v2)).isNotEmpty();
    }

    @Test
    void statementAndPayoutAggregateIdsAreAgentColonYm() {
        for (String file : List.of("a1#11", "a1#6", "a1#7")) {
            ObjectNode doc = sample(file);
            String agent = doc.path("payload").path("agentId").asString();
            assertThat(doc.path("aggregate").path("id").asString()).isEqualTo(agent + ":" + doc.path("payload").path("closeYm").asString());
            ((ObjectNode) doc.path("aggregate")).put("id", agent);
            assertThat(schema(ENVELOPE).validate(doc)).as(file).isNotEmpty();
            ((ObjectNode) doc.path("aggregate")).put("id", agent + ":202613");
            assertThat(schema(ENVELOPE).validate(doc)).as(file).isNotEmpty();
        }
    }

    @Test
    void unknownFieldsAreAllowedInTheEnvelopeAndThePayload() {
        ObjectNode doc = sample("a1#2");
        doc.put("traceId", "abc");
        ((ObjectNode) doc.path("payload")).put("futureField", 1);
        ((ObjectNode) doc.path("payload").path("basis")).put("tier", "B");
        assertThat(schema(ENVELOPE).validate(doc)).isEmpty();
    }

    // ------------------------------------------------------------------ Q3·I1 — 소수는 문자열, amount의 뜻

    @Test
    void ratesAndMultiplesAreDecimalStringsNotNumbers() {
        ObjectNode calc = sample("a1#2");
        ((ObjectNode) calc.path("payload").path("basis")).put("rate", 9.333333);
        assertThat(schema(ENVELOPE).validate(calc)).as("basis.rate as a JSON number").isNotEmpty();
        ObjectNode calc2 = sample("a1#2");
        ((ObjectNode) calc2.path("payload").path("basis")).put("payoutRate", "0.1234567");
        assertThat(schema(ENVELOPE).validate(calc2)).as("more than 6 decimals").isNotEmpty();
        ObjectNode opened = sample("a1#1");
        ((ObjectNode) opened.path("payload")).put("limitMultiple", 12);
        assertThat(schema(ENVELOPE).validate(opened)).as("limitMultiple as a JSON number").isNotEmpty();
        ((ObjectNode) opened.path("payload")).put("limitMultiple", "012");
        assertThat(schema(ENVELOPE).validate(opened)).as("leading zero").isNotEmpty();

        JsonNode decimal = read(CONTRACTS.resolve("events/v1/defs.schema.json")).path("$defs").path("decimalString");
        assertThat(decimal.path("pattern").asString()).isEqualTo("^(0|[1-9][0-9]*)(\\.[0-9]{1,6})?$");
        assertThat(decimal.path("description").asString()).contains("표시 전용").contains("산술에 쓰지 않는다");
    }

    @Test
    void amountIsDocumentedAsPaidAfterTheLimitCut() {
        JsonNode amount = payloadSchema("CommissionCalculated").path("properties").path("amount");
        // K6(v1.1): 삭감 뒤·분할 뒤 지급 예정액, 요청액 = amount + limitCutAmt, 이연분은 DeferralScheduled(CURVE·LIMIT_DEFER)
        assertThat(amount.path("description").asString()).contains("한도 삭감 뒤·분급 분할 뒤 지급 예정액").contains("amount + limitCutAmt")
                .contains("DeferralScheduled").contains("CURVE").contains("LIMIT_DEFER");
        ObjectNode calc = sample("a1#2");
        ((ObjectNode) calc.path("payload")).put("amount", 2_800_000.5);
        assertThat(schema(ENVELOPE).validate(calc)).as("non-integer amount").isNotEmpty();
    }

    // ------------------------------------------------------------------ v1.1 — 엔진에 없는 데이터는 선택(K1~K5), 정의 문장(K7·K9)

    static Stream<Arguments> optionalInV11() {
        return Stream.of(
                Arguments.argumentSet("K1 policySummary.productName·insuredMasked", "a1#2", List.of("policySummary"),
                        List.of("productName", "insuredMasked"), List.of("contractDate", "monthlyPremium")),
                Arguments.argumentSet("K2 basis.rate·payoutRate·installmentNo", "a1#2", List.of("basis"),
                        List.of("rate", "payoutRate", "installmentNo"), List.of("ruleVersionIds")),
                Arguments.argumentSet("K3 AgentSnapshot.grade·role", "16-AgentSnapshot.json", List.<String>of(),
                        List.of("grade"), List.of("agentId", "name", "orgPath", "status", "validFrom")),
                Arguments.argumentSet("K4 StatementConfirmed.clawbackOffset·withholdingTax·net", "a1#6", List.<String>of(),
                        List.of("clawbackOffset"), List.of("agentId", "closeYm", "gross", "adjustment", "closedAt")),
                Arguments.argumentSet("L1 LimitLedgerRebased.postingSeq (v1.1.1)", "17-LimitLedgerRebased.json", List.<String>of(),
                        List.of("postingSeq"), List.of("ledgerId", "oldLimit", "newLimit", "monthlyPremium", "reason")));
    }

    /** 샘플에 없는 선택 필드를 채울 유효한 값(허구). */
    private static final Map<String, JsonNode> FILL = Map.of(
            "productName", JsonNodeFactory.instance.stringNode("허구 종신"), "insuredMasked", JsonNodeFactory.instance.stringNode("허*"),
            "rate", JsonNodeFactory.instance.stringNode("7"), "payoutRate", JsonNodeFactory.instance.stringNode("0.9"),
            "installmentNo", JsonNodeFactory.instance.numberNode(1), "grade", JsonNodeFactory.instance.stringNode("G3"),
            "clawbackOffset", JsonNodeFactory.instance.numberNode(0), "postingSeq", JsonNodeFactory.instance.numberNode(1));

    /**
     * 선택이 된 필드를 전부 뺀 변형이 통과하고, 그 객체의 required는 정확히 남은 목록이다(다시 필수로 조여지면 여기서 보인다).
     * v1.2(M4)부터 샘플 16에는 {@code role}이, 08에는 {@code withholdingTax}·{@code net}이 처음부터 없다 — 샘플 자체가 통과하는 것과
     * 정확한 required 목록이 그 셋이 선택임을 증명한다.
     */
    @ParameterizedTest
    @MethodSource("optionalInV11")
    void fieldsTheEngineDoesNotHaveAreOptional(String sampleFile, List<String> objectPath, List<String> optional, List<String> required) {
        ObjectNode doc = sample(sampleFile);
        ObjectNode target = (ObjectNode) doc.path("payload");
        JsonNode schemaNode = payloadSchema(doc.path("type").asString());
        for (String step : objectPath) {
            target = (ObjectNode) target.path(step);
            schemaNode = schemaNode.path("properties").path(step);
        }
        for (String field : optional) {
            if (!target.has(field)) {
                // v1.3: 엔진 A-1 줄에는 엔진에 없는 값이 처음부터 없다 — 유효한 값을 넣어 통과를 본 뒤 빼야 "뺀 변형"이 의미를 갖는다
                target.set(field, FILL.get(field));
            }
        }
        assertThat(schema(ENVELOPE).validate(doc)).as("with the optional fields").isEmpty();
        for (String field : optional) {
            target.remove(field);
        }
        assertThat(schema(ENVELOPE).validate(doc)).isEmpty();
        assertThat(schemaNode.path("required")).extracting(JsonNode::asString).containsExactlyInAnyOrderElementsOf(required);
    }

    @Test
    void payoutCompletedCarriesTheRunsSettledAmountsAsOptionalIntegers() {
        ObjectNode doc = sample("a1#7");
        ObjectNode payload = (ObjectNode) doc.path("payload");
        // 엔진 A-1 지급(월납 150,000): 세전 1,800,000, 원천세 59,400, 순액 1,740,600 = paidAmount — adjustment는 엔진이 싣지 않으므로 더해 본다
        payload.put("runSeq", 1).put("gross", 1_800_000).put("clawbackOffset", 0).put("receivableOffset", 0).put("carriedReceivable", 0)
                .put("adjustment", 0).put("payable", 1_800_000).put("withholdingTax", 59_400).put("net", 1_740_600);
        assertThat(schema(ENVELOPE).validate(doc)).isEmpty();
        assertThat(payload.path("net").asLong()).isEqualTo(payload.path("paidAmount").asLong());
        for (String field : List.of("gross", "clawbackOffset", "receivableOffset", "carriedReceivable", "adjustment", "payable", "withholdingTax", "net")) {
            ObjectNode bad = doc.deepCopy();
            ((ObjectNode) bad.path("payload")).put(field, "1");
            assertThat(schema(ENVELOPE).validate(bad)).as("%s as a string", field).isNotEmpty();
        }
        ObjectNode run0 = doc.deepCopy();
        ((ObjectNode) run0.path("payload")).put("runSeq", 0);
        assertThat(schema(ENVELOPE).validate(run0)).as("runSeq 0").isNotEmpty();
        assertThat(payloadSchema("PayoutCompleted").path("required")).extracting(JsonNode::asString)
                .containsExactlyInAnyOrder("agentId", "closeYm", "paidAt", "paidAmount");
        JsonNode props = payloadSchema("PayoutCompleted").path("properties");
        assertThat(props.path("receivableOffset").path("description").asString()).contains("clawbackOffset").contains("다른 것");
        assertThat(props.path("paidAmount").path("description").asString()).contains("= net");
    }

    /** K7: 세 집계 이벤트의 gross·clawbackOffset·adjustment는 포털 earnings_month 합산과 같은 정의 문장을 갖는다. */
    @Test
    void statementAndPayoutTotalsShareTheEarningsMonthDefinitions() {
        for (String type : List.of("StatementEstimated", "StatementConfirmed", "PayoutCompleted")) {
            JsonNode props = payloadSchema(type).path("properties");
            assertThat(props.path("gross").path("description").asString()).as(type).contains("commType ∉ {CLAWBACK, ADJUSTMENT}").contains("전 상태 합산");
            assertThat(props.path("clawbackOffset").path("description").asString()).as(type).contains("commType = CLAWBACK").contains("≤ 0");
            assertThat(props.path("adjustment").path("description").asString()).as(type).contains("AdjustmentPosted");
        }
    }

    /**
     * v1.4.1(DD-19, 포털 Phase 8 ⓪a) — 표기만 바뀌었다: 이벤트 스키마 15개 전부에서 문자열 {@code description}을 뺀 트리(키 정렬)가
     * 1.4.0과 같다. 다이제스트는 1.4.0(`main` = `2ccfc44`)에서 잰 값이다 — 제약을 바꾸는 개정은 이 값을 바꿔야 하고, 그것은 표기 변경이 아니다.
     */
    @Test
    void descriptionOnlyChangesSince140() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(CONTRACTS.resolve("events/v1"))) {
            files = walk.filter(p -> p.toString().endsWith(".schema.json")).sorted().toList();
        }
        assertThat(files).hasSize(15);
        StringBuilder all = new StringBuilder();
        for (Path p : files) {
            all.append(CONTRACTS.relativize(p).toString().replace('\\', '/')).append('\n')
                    .append(JSON.writeValueAsString(withoutDescriptions(read(p)))).append('\n');
        }
        assertThat(sha256(all.toString())).isEqualTo("f26fd753e779e4086da52e1fc54416b5f1271f9787ef08a8df99e5518c6c1926");
    }

    private static JsonNode withoutDescriptions(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (!(e.getKey().equals("description") && e.getValue().isString())) {
                    fields.put(e.getKey(), withoutDescriptions(e.getValue()));
                }
            }
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(v -> copy.add(withoutDescriptions(v)));
            return copy;
        }
        return node;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** v1.4.1(DD-19): 잠정 명세의 gross는 그 달 도래 예정 분급의 미리보기를 더할 수 있다 — 포털 Σ와 같다는 보장이 없고, basisSeq는 하한이다. */
    @Test
    void v141EstimatedGrossDocumentsThePreview() {
        String gross = payloadSchema("StatementEstimated").path("properties").path("gross").path("description").asString();
        assertThat(gross).contains("도래 예정 분급").contains("미리보기").contains("같다는 보장이 없다").contains("basisSeq는 하한")
                .doesNotContain("포털 earnings_month.gross와 같은 정의");
        for (String type : List.of("StatementConfirmed", "PayoutCompleted")) {
            assertThat(payloadSchema(type).path("properties").path("gross").path("description").asString()).as(type)
                    .doesNotContain("미리보기");
        }
    }

    @Test
    void policyEventTypesDocumentTheEngineMapping() {
        JsonNode eventType = payloadSchema("PolicyEventObserved").path("properties").path("eventType");
        assertThat(eventType.path("enum")).extracting(JsonNode::asString)
                .containsExactly("CANCEL", "LAPSE", "WITHDRAW", "REDUCE", "REINSTATE", "PAYMENT_OVERDUE");
        assertThat(eventType.path("description").asString()).contains("REINSTATE = 엔진 REVIVE").contains("PAYMENT_OVERDUE는 우리 엔진이 발행하지 않는다");
    }

    /** v1.1.1 L1~L3: postingSeq는 있으면 여전히 ≥ 1, paidAt·limitCutPolicy의 뜻이 설명문에 있다. */
    @Test
    void v111DescriptionsAndTheRemainingPostingSeqBound() {
        ObjectNode rebased = sample("17-LimitLedgerRebased.json");
        ((ObjectNode) rebased.path("payload")).put("postingSeq", 0);
        assertThat(schema(ENVELOPE).validate(rebased)).as("postingSeq 0").isNotEmpty();
        assertThat(payloadSchema("LimitLedgerRebased").path("properties").path("postingSeq").path("description").asString())
                .contains("재산정 직전 마지막 전기 순번");
        assertThat(payloadSchema("PayoutCompleted").path("properties").path("paidAt").path("description").asString())
                .contains("지급 확정").contains("이체 파일 실행 시각이 아니다");
        assertThat(payloadSchema("CommissionCalculated").path("properties").path("limitCutPolicy").path("description").asString())
                .contains("over_limit_action 그대로").contains("DEFER_AFTER_FY도 삭감과 같게").contains("LIMIT_DEFER 스케줄이 없어도 오류로 보지 않는다");
    }

    /** 포털 defs의 시각·UUID 형식은 공유 핵심의 것과 글자까지 같다(두 정본이 어긋나지 않게). */
    @Test
    void portalInstantPatternEqualsTheCore() {
        JsonNode core = read(CONTRACTS.resolve("shared/ga-disclosure/events/v1/envelope-core.schema.json")).path("$defs");
        JsonNode portal = read(CONTRACTS.resolve("events/v1/defs.schema.json")).path("$defs");
        assertThat(portal.path("instant").path("pattern").asString()).isEqualTo(core.path("instant").path("pattern").asString());
    }

    // ------------------------------------------------------------------ format 0 (H3 — contracts/ 전 파일)

    @Test
    void noContractFileHasAFormatKeyword() throws IOException {
        Map<String, Integer> found = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(CONTRACTS)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".jsonl") || p.toString().endsWith(".yaml")
                            || p.toString().endsWith(".yml"))
                    .toList();
        }
        for (Path p : files) {
            String text = Files.readString(p);
            int n = 0;
            if (p.toString().endsWith(".jsonl")) {
                for (String line : text.split("\n")) {
                    n += line.isBlank() ? 0 : formatKeywords(JSON.readTree(line));
                }
            } else {
                n = formatKeywords(p.toString().endsWith(".json") ? JSON.readTree(text) : YAML.readTree(text));
            }
            if (n > 0) {
                found.put(CONTRACTS.relativize(p).toString(), n);
            }
        }
        assertThat(files.stream().map(f -> CONTRACTS.relativize(f).toString().replace('\\', '/')).toList()).as("contract files scanned")
                .contains("shared/ga-disclosure/events/v1/envelope-core.schema.json", "events/v1/envelope.schema.json",
                        "events/v1/defs.schema.json", "events/v1/payloads/CommissionCalculated.v1.schema.json",
                        "api/v1/engine-internal.openapi.yaml", "api/v1/portal-api.openapi.yaml")
                .contains("shared/ga-commission-engine/samples/simulations/index.json",
                        "shared/ga-commission-engine/samples/statements/a1/index.json")
                .hasSize(1 + 2 + 13 + (1 + 6) + 2 + 27 + 19);   // 샘플: A-1 스트림 하나 + 단건 6(v1.4 — 07은 a1 seq 11의 실 샘플로), API 둘(Phase 3 — 포털 조회 API),
                                                                // 엔진 시뮬레이션 샘플 27(포털 Phase 5 — 요청·응답 13쌍 + index),
                                                                // 엔진 명세 샘플 19(포털 Phase 7 — 묶음 셋: 요청·응답 8쌍 + index 셋)
        assertThat(found).isEmpty();
    }

    private static int formatKeywords(JsonNode node) {
        int n = 0;
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (e.getKey().equals("format")) {
                    n++;
                }
                n += formatKeywords(e.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                n += formatKeywords(child);
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ OpenAPI — 피드 정수 커서 규약

    @Test
    void feedUsesIntegerCursorsAndAFeedOfSamplesIsValid() {
        JsonNode api = YAML.readTree(readString(CONTRACTS.resolve("api/v1/engine-internal.openapi.yaml")));
        assertThat(api.path("openapi").asString()).isEqualTo("3.1.0");
        JsonNode feed = api.path("components").path("schemas").path("FeedResponse");
        assertThat(feed.path("required")).extracting(JsonNode::asString)
                .containsExactlyInAnyOrder("tenantId", "events", "nextSeq", "headSeq", "schemaVersion");
        for (String f : List.of("nextSeq", "headSeq", "schemaVersion")) {
            assertThat(feed.path("properties").path(f).path("type").asString()).as(f).isEqualTo("integer");
        }
        Map<String, JsonNode> params = new TreeMap<>();
        api.path("paths").path("/internal/v1/events").path("get").path("parameters").forEach(p -> params.put(p.path("name").asString(), p.path("schema")));
        assertThat(params.get("afterSeq").path("type").asString()).isEqualTo("integer");
        assertThat(params.get("afterSeq").path("minimum").asInt()).isZero();
        assertThat(params.get("limit").path("minimum").asInt()).isEqualTo(1);
        assertThat(params.get("limit").path("maximum").asInt()).isEqualTo(1000);
        assertThat(params.get("limit").path("default").asInt()).isEqualTo(200);
        JsonNode responses = api.path("paths").path("/internal/v1/events").path("get").path("responses");
        assertThat(responses.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("200", "400", "401", "422");
        // M1(v1.2): 파라미터 오류는 400 INVALID_REQUEST(Problem) — 엔진 E1이 이미 낸다
        assertThat(responses.path("400").path("description").asString()).contains("INVALID_REQUEST");
        assertThat(responses.path("400").path("content").path("application/json").path("schema").path("$ref").asString())
                .isEqualTo("#/components/schemas/Problem");
        assertThat(responses.path("422").path("description").asString()).contains("AFTER_BEYOND_HEAD");
        assertThat(api.path("paths").properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "/internal/v1/events", "/internal/v1/simulations/new-contract", "/internal/v1/simulations/policy-event",
                "/internal/v1/agents/{agentId}/statements/{ym}");

        Schema feedSchema = schema(PORTAL + "api/v1/engine-internal.openapi.yaml#/components/schemas/FeedResponse");
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        ArrayNode events = response.putArray("events");
        samples().stream().limit(9).map(Sample::doc).forEach(events::add);
        response.put("tenantId", "GA01").put("nextSeq", 9).put("headSeq", 10).put("schemaVersion", 1);
        assertThat(feedSchema.validate(response)).isEmpty();
        ObjectNode noTenant = response.deepCopy();
        noTenant.remove("tenantId");
        assertThat(feedSchema.validate(noTenant)).as("without tenantId (K8)").isNotEmpty();
        ObjectNode badTenant = response.deepCopy();
        badTenant.put("tenantId", "ga01");
        assertThat(feedSchema.validate(badTenant)).as("tenantId not in the TenantId format").isNotEmpty();
        response.put("nextSeq", "9");
        assertThat(feedSchema.validate(response)).as("string cursor").isNotEmpty();

        Schema problem = schema(PORTAL + "api/v1/engine-internal.openapi.yaml#/components/schemas/Problem");
        assertThat(problem.validate(JSON.readTree("{\"code\":\"AFTER_BEYOND_HEAD\",\"message\":\"afterSeq 20 > headSeq 13\"}"))).isEmpty();
        assertThat(problem.validate(JSON.readTree("{\"message\":\"x\"}"))).as("Problem without code").isNotEmpty();
    }
}

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 *   <li>샘플 17개(부록 A-1 seq 1~13 + 4)가 13종을 덮고 전부 통과, 필수 필드를 하나씩 뺀 변형은 전부 실패.</li>
 *   <li>포털 Envelope ⊇ 핵심: 핵심이 거부하는 변형을 포털 Envelope도 거부하고, 포털 Envelope의 allOf 첫 항이 사본을 가리킨다.</li>
 *   <li>type ↔ payload, type ↔ aggregate.kind 짝(Q4·Q5), 열거 밖 type·version은 거부(else: false), 모르는 필드는 허용(§4.5).</li>
 *   <li>요율·배수는 소수 문자열(Q3) — JSON number는 거부. contracts/ 전 파일 format 0. OpenAPI 피드의 정수 커서 규약.</li>
 *   <li>v1.1(Phase 1 지시문 §0): 엔진에 없는 데이터는 선택(K1~K4), PayoutCompleted 확정값 선택 필드(K5), 금액 정의 문장(K6·K7),
 *       피드 응답 필수 tenantId·422 Problem·limit 기본 200(K8), PolicyEventObserved 열거 설명(K9).</li>
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

    static List<Path> samples() {
        try (Stream<Path> files = Files.list(CONTRACTS.resolve("events/v1/samples"))) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Stream<Arguments> sampleFiles() {
        return samples().stream().map(p -> Arguments.argumentSet(p.getFileName().toString(), p));
    }

    private static ObjectNode payloadSchema(String type) {
        return read(CONTRACTS.resolve("events/v1/payloads/" + type + ".v1.schema.json"));
    }

    // ------------------------------------------------------------------ 샘플

    @Test
    void samplesCoverThe13TypesInSeqOrderOfAppendixA1() {
        List<ObjectNode> all = samples().stream().map(EventSchemaTest::read).toList();
        assertThat(all).hasSize(17);
        assertThat(all.stream().map(s -> s.path("type").asString()).distinct()).containsExactlyInAnyOrderElementsOf(EVENT_TYPES);
        assertThat(all.stream().map(s -> s.path("seq").asLong())).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L);
        // A-1 seq 3~5: 한도 3,600,000에 2,800,000 + 800,000(삭감 200,000 뒤) = 3,600,000 — amount는 삭감 뒤 지급분(I1)
        assertThat(all.get(3).path("payload").path("amount").asLong()).isEqualTo(800_000L);
        assertThat(all.get(3).path("payload").path("limitCutAmt").asLong()).isEqualTo(200_000L);
        assertThat(all.get(4).path("payload").path("accumAfter").asLong()).isEqualTo(3_600_000L);
    }

    @ParameterizedTest
    @MethodSource("sampleFiles")
    void everySamplePassesTheEnvelopeTheCoreAndItsPayloadSchema(Path file) {
        ObjectNode sample = read(file);
        String type = sample.path("type").asString();
        assertThat(schema(ENVELOPE).validate(sample)).isEmpty();
        assertThat(schema(CORE_ID).validate(sample)).isEmpty();
        assertThat(schema(PORTAL + "events/v1/payloads/" + type + ".v1.schema.json").validate(sample.path("payload"))).isEmpty();
        assertThat(sample.path("aggregate").path("kind").asString()).isEqualTo(KINDS.get(type));
    }

    /** 각 샘플에서 필수 필드(Envelope 일곱 개 + payload의 required, 안쪽 객체의 required까지)를 하나씩 뺀 변형은 전부 실패한다. */
    @ParameterizedTest
    @MethodSource("sampleFiles")
    void removingAnyRequiredFieldFails(Path file) {
        ObjectNode sample = read(file);
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
        ObjectNode doc = read(CONTRACTS.resolve("events/v1/samples/02-CommissionCalculated.json"));
        mutation.apply(doc);
        assertThat(schema(CORE_ID).validate(doc)).as("core").isNotEmpty();
        assertThat(schema(ENVELOPE).validate(doc)).as("portal envelope").isNotEmpty();
    }

    // ------------------------------------------------------------------ 짝·열거·열린 필드

    @Test
    void aPayloadOfAnotherTypeIsRejected() {
        Map<String, ObjectNode> byType = new TreeMap<>();
        samples().stream().map(EventSchemaTest::read).forEach(s -> byType.putIfAbsent(s.path("type").asString(), s));
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
    void aggregateKindMustBeTheOnePairedWithTheType(Path file) {
        ObjectNode sample = read(file);
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
        ObjectNode unknown = read(CONTRACTS.resolve("events/v1/samples/09-PayoutCompleted.json"));
        unknown.put("type", "BonusGranted");
        assertThat(schema(CORE_ID).validate(unknown)).isEmpty();
        assertThat(schema(ENVELOPE).validate(unknown)).isNotEmpty();

        ObjectNode v2 = read(CONTRACTS.resolve("events/v1/samples/09-PayoutCompleted.json"));
        v2.put("version", 2);
        assertThat(schema(CORE_ID).validate(v2)).isEmpty();
        assertThat(schema(ENVELOPE).validate(v2)).isNotEmpty();
    }

    @Test
    void statementAndPayoutAggregateIdsAreAgentColonYm() {
        for (String file : List.of("07-StatementEstimated.json", "08-StatementConfirmed.json", "09-PayoutCompleted.json")) {
            ObjectNode doc = read(CONTRACTS.resolve("events/v1/samples/" + file));
            assertThat(doc.path("aggregate").path("id").asString()).isEqualTo("A0001:202608");
            ((ObjectNode) doc.path("aggregate")).put("id", "A0001");
            assertThat(schema(ENVELOPE).validate(doc)).as(file).isNotEmpty();
            ((ObjectNode) doc.path("aggregate")).put("id", "A0001:202613");
            assertThat(schema(ENVELOPE).validate(doc)).as(file).isNotEmpty();
        }
    }

    @Test
    void unknownFieldsAreAllowedInTheEnvelopeAndThePayload() {
        ObjectNode doc = read(CONTRACTS.resolve("events/v1/samples/02-CommissionCalculated.json"));
        doc.put("traceId", "abc");
        ((ObjectNode) doc.path("payload")).put("futureField", 1);
        ((ObjectNode) doc.path("payload").path("basis")).put("tier", "B");
        assertThat(schema(ENVELOPE).validate(doc)).isEmpty();
    }

    // ------------------------------------------------------------------ Q3·I1 — 소수는 문자열, amount의 뜻

    @Test
    void ratesAndMultiplesAreDecimalStringsNotNumbers() {
        ObjectNode calc = read(CONTRACTS.resolve("events/v1/samples/02-CommissionCalculated.json"));
        ((ObjectNode) calc.path("payload").path("basis")).put("rate", 9.333333);
        assertThat(schema(ENVELOPE).validate(calc)).as("basis.rate as a JSON number").isNotEmpty();
        ObjectNode calc2 = read(CONTRACTS.resolve("events/v1/samples/02-CommissionCalculated.json"));
        ((ObjectNode) calc2.path("payload").path("basis")).put("payoutRate", "0.1234567");
        assertThat(schema(ENVELOPE).validate(calc2)).as("more than 6 decimals").isNotEmpty();
        ObjectNode opened = read(CONTRACTS.resolve("events/v1/samples/01-LimitLedgerOpened.json"));
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
        ObjectNode calc = read(CONTRACTS.resolve("events/v1/samples/02-CommissionCalculated.json"));
        ((ObjectNode) calc.path("payload")).put("amount", 2_800_000.5);
        assertThat(schema(ENVELOPE).validate(calc)).as("non-integer amount").isNotEmpty();
    }

    // ------------------------------------------------------------------ v1.1 — 엔진에 없는 데이터는 선택(K1~K5), 정의 문장(K7·K9)

    static Stream<Arguments> optionalInV11() {
        return Stream.of(
                Arguments.argumentSet("K1 policySummary.productName·insuredMasked", "02-CommissionCalculated.json", List.of("policySummary"),
                        List.of("productName", "insuredMasked"), List.of("contractDate", "monthlyPremium")),
                Arguments.argumentSet("K2 basis.rate·payoutRate·installmentNo", "02-CommissionCalculated.json", List.of("basis"),
                        List.of("rate", "payoutRate", "installmentNo"), List.of("ruleVersionIds")),
                Arguments.argumentSet("K3 AgentSnapshot.grade·role", "16-AgentSnapshot.json", List.<String>of(),
                        List.of("grade", "role"), List.of("agentId", "name", "orgPath", "status", "validFrom")),
                Arguments.argumentSet("K4 StatementConfirmed.clawbackOffset·withholdingTax·net", "08-StatementConfirmed.json", List.<String>of(),
                        List.of("clawbackOffset", "withholdingTax", "net"), List.of("agentId", "closeYm", "gross", "adjustment", "closedAt")),
                Arguments.argumentSet("L1 LimitLedgerRebased.postingSeq (v1.1.1)", "17-LimitLedgerRebased.json", List.<String>of(),
                        List.of("postingSeq"), List.of("ledgerId", "oldLimit", "newLimit", "monthlyPremium", "reason")));
    }

    /** 선택이 된 필드를 전부 뺀 변형이 통과하고, 그 객체의 required는 정확히 남은 목록이다(다시 필수로 조여지면 여기서 보인다). */
    @ParameterizedTest
    @MethodSource("optionalInV11")
    void fieldsTheEngineDoesNotHaveAreOptional(String sampleFile, List<String> objectPath, List<String> optional, List<String> required) {
        ObjectNode doc = read(CONTRACTS.resolve("events/v1/samples/" + sampleFile));
        ObjectNode target = (ObjectNode) doc.path("payload");
        JsonNode schemaNode = payloadSchema(doc.path("type").asString());
        for (String step : objectPath) {
            target = (ObjectNode) target.path(step);
            schemaNode = schemaNode.path("properties").path(step);
        }
        for (String field : optional) {
            assertThat(target.has(field)).as("sample has %s", field).isTrue();
            target.remove(field);
        }
        assertThat(schema(ENVELOPE).validate(doc)).isEmpty();
        assertThat(schemaNode.path("required")).extracting(JsonNode::asString).containsExactlyInAnyOrderElementsOf(required);
    }

    @Test
    void payoutCompletedCarriesTheRunsSettledAmountsAsOptionalIntegers() {
        ObjectNode doc = read(CONTRACTS.resolve("events/v1/samples/09-PayoutCompleted.json"));
        ObjectNode payload = (ObjectNode) doc.path("payload");
        payload.put("runSeq", 1).put("gross", 3_600_000).put("clawbackOffset", 0).put("receivableOffset", 0).put("carriedReceivable", 0)
                .put("adjustment", 0).put("payable", 3_600_000).put("withholdingTax", 118_800).put("net", 3_481_200);
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
        ObjectNode rebased = read(CONTRACTS.resolve("events/v1/samples/17-LimitLedgerRebased.json"));
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
                    .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .toList();
        }
        for (Path p : files) {
            String text = Files.readString(p);
            int n = formatKeywords(p.toString().endsWith(".json") ? JSON.readTree(text) : YAML.readTree(text));
            if (n > 0) {
                found.put(CONTRACTS.relativize(p).toString(), n);
            }
        }
        assertThat(files.stream().map(f -> CONTRACTS.relativize(f).toString().replace('\\', '/')).toList()).as("contract files scanned")
                .contains("shared/ga-disclosure/events/v1/envelope-core.schema.json", "events/v1/envelope.schema.json",
                        "events/v1/defs.schema.json", "events/v1/payloads/CommissionCalculated.v1.schema.json",
                        "api/v1/engine-internal.openapi.yaml")
                .hasSize(1 + 2 + 13 + 17 + 1);
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
        assertThat(responses.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("200", "401", "422");
        assertThat(responses.path("422").path("description").asString()).contains("AFTER_BEYOND_HEAD");
        assertThat(api.path("paths").properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "/internal/v1/events", "/internal/v1/simulations/new-contract", "/internal/v1/simulations/policy-event",
                "/internal/v1/agents/{agentId}/statements/{ym}");

        Schema feedSchema = schema(PORTAL + "api/v1/engine-internal.openapi.yaml#/components/schemas/FeedResponse");
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        ArrayNode events = response.putArray("events");
        samples().stream().limit(9).map(EventSchemaTest::read).forEach(events::add);
        response.put("tenantId", "GA01").put("nextSeq", 9).put("headSeq", 13).put("schemaVersion", 1);
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

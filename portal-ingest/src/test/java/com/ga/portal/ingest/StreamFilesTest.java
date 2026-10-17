package com.ga.portal.ingest;

import com.ga.portal.ingest.testing.Samples;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 시험 스트림(계획 ⑧, {@code streams/README.md}의 표)을 샘플에서 다시 계산해 파일과 대조한다 — 샘플과 스트림이 어긋나지 않게. */
class StreamFilesTest {

    private static List<ObjectNode> range(long from, long to) {
        return LongStream.rangeClosed(from, to).mapToObj(Samples::seq).toList();
    }

    private static List<ObjectNode> of(long... seqs) {
        return LongStream.of(seqs).mapToObj(Samples::seq).toList();
    }

    private static ObjectNode changed(long seq, UnaryOperator<ObjectNode> change) {
        return change.apply(Samples.seq(seq));
    }

    @SafeVarargs
    private static List<ObjectNode> concat(List<ObjectNode>... parts) {
        List<ObjectNode> out = new ArrayList<>();
        for (List<ObjectNode> p : parts) {
            out.addAll(p);
        }
        return out;
    }

    static Stream<Arguments> recipes() {
        return Stream.of(
                Arguments.of("a1-golden.jsonl", "GA01", range(1, 17)),
                Arguments.of("dup.jsonl", "GA01", of(1, 2, 3, 3, 4, 5)),
                Arguments.of("replay-known.jsonl", "GA01", of(1, 2, 3, 4, 5, 3)),
                Arguments.of("replay-new.jsonl", "GA01", concat(range(1, 5),
                        List.of(changed(3, e -> e.put("eventId", "5eed0000-0000-4000-8000-0000000000f3"))))),
                Arguments.of("gap.jsonl", "GA01", concat(range(1, 4), range(6, 17))),
                Arguments.of("poison-schema.jsonl", "GA01", concat(range(1, 4),
                        List.of(changed(5, e -> {
                            ((ObjectNode) e.path("payload")).remove("accumAfter");
                            return e;
                        })), range(6, 17))),
                Arguments.of("poison-kind.jsonl", "GA01", concat(range(1, 4),
                        List.of(changed(5, e -> {
                            ((ObjectNode) e.path("aggregate")).put("kind", "POLICY");
                            return e;
                        })), range(6, 17))),
                Arguments.of("hash-mismatch.jsonl", "GA01", concat(range(1, 5),
                        List.of(changed(5, e -> {
                            ((ObjectNode) e.path("payload")).put("accumAfter", e.path("payload").path("accumAfter").asLong() - 1);
                            return e;
                        })))),
                Arguments.of("version2.jsonl", "GA01", concat(range(1, 4), List.of(changed(5, e -> e.put("version", 2))), range(6, 17))),
                Arguments.of("other-tenant.jsonl", "GA02", range(1, 17)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recipes")
    void streamEqualsItsRecipe(String file, String tenant, List<ObjectNode> expected) throws IOException {
        List<String> lines = Files.readAllLines(Samples.stream(file), StandardCharsets.UTF_8);
        JsonNode header = Samples.JSON.readTree(lines.getFirst());
        assertThat(header.path("tenantId").asString()).isEqualTo(tenant);
        assertThat(header.path("schemaVersion").asInt()).isEqualTo(1);
        List<String> actual = lines.subList(1, lines.size()).stream().map(l -> Samples.JSON.readTree(l).toString()).toList();
        assertThat(actual).containsExactlyElementsOf(expected.stream().map(JsonNode::toString).toList());
    }
}

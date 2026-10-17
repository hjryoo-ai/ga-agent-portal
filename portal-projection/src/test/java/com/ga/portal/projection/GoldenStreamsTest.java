package com.ga.portal.projection;

import com.ga.portal.projection.testing.Envelopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 골든 스트림({@code golden-streams/README.md}): 엔진 사본 둘은 바이트 고정, A-1 변형 셋은 A-1에서 다시 만들어 대조한다. */
class GoldenStreamsTest {

    @ParameterizedTest
    @CsvSource({
            "contract,25929e7f9908227cd943f75da02b5e0313483a1e05731d785cbbe66477abe718",
            "engine-e1-cycle.jsonl,407087437945683246a54f58610957919e7fc51e40cf112f819ae0164d1f64c6"})
    void engineCopiesAreByteForByte(String name, String sha256) throws IOException, NoSuchAlgorithmException {
        Path file = name.equals("contract") ? Envelopes.a1() : Envelopes.stream(name);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))).isEqualTo(sha256);
    }

    @Test
    void a1VariantsFollowTheirRecipes() {
        List<ObjectNode> a1 = Envelopes.nodes(Envelopes.a1());
        assertRecipe("a1-interleaved.jsonl", pick(a1, 1, 2, 4, 3, 5, 6, 7, 8, 9, 10));
        assertRecipe("a1-posted-first.jsonl", pick(a1, 1, 4, 5, 2, 3, 6, 7, 8, 10, 9));
        List<ObjectNode> estimated = new ArrayList<>(pick(a1, 1, 2, 3, 4, 5));
        estimated.add((ObjectNode) Envelopes.JSON.readTree("""
                {"eventId":"5eed0000-0000-4000-8000-0000000000e1","seq":0,"type":"StatementEstimated","version":1,
                 "occurredAt":"2026-09-01T02:00:00+09:00","aggregate":{"kind":"STATEMENT","id":"A-1001:202608"},
                 "payload":{"agentId":"A-1001","closeYm":"202608","gross":1800000,"clawbackOffset":0,"adjustment":0,"withholdingTax":59400,
                            "net":1740600,"basisSeq":5,"asOf":"2026-09-01T02:00:00+09:00"}}"""));
        estimated.addAll(pick(a1, 6, 7, 8, 9, 10));
        assertRecipe("a1-estimated.jsonl", estimated);
    }

    private static List<ObjectNode> pick(List<ObjectNode> a1, int... seqs) {
        List<ObjectNode> out = new ArrayList<>();
        for (int s : seqs) {
            out.add(a1.get(s - 1).deepCopy());
        }
        return out;
    }

    private static void assertRecipe(String file, List<ObjectNode> events) {
        for (int i = 0; i < events.size(); i++) {
            events.get(i).put("seq", i + 1);
        }
        assertThat(Envelopes.nodes(Envelopes.stream(file)).stream().map(ObjectNode::toString))
                .as(file).containsExactlyElementsOf(events.stream().map(ObjectNode::toString).toList());
    }
}

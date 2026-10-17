package com.ga.portal.ingest.testing;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 계약 샘플(저장소 {@code contracts/events/v1/samples} — 허구 데이터)과 시험 스트림(이 모듈 시험 자원 {@code streams/}).
 *
 * <p>계약 v1.3부터 샘플은 셋이다: {@code a1/a1_portal_150000_e2.jsonl}(v1.4 — 엔진 E2 ⓪의 둘째 판 바이트 그대로, seq 1~11),
 * {@code contract-only/}·{@code standalone/}(단건 예시 6개 — 각자의 seq는 예시값이라 a1과 겹친다). 시험이 쓰는 <b>정규 스트림</b>
 * {@link #all()}은 a1의 11건 뒤에 단건 예시 6개를 파일 이름 순으로 이어 seq를 12~17로 다시 매긴 것이다(eventId는 그대로 — 셋이 겹치지 않는다).
 * 해시는 seq를 보지 않으므로(Phase 1 Q1) 다시 매겨도 각 이벤트의 해시는 샘플 파일의 것과 같다.
 */
public final class Samples {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    private Samples() {
    }

    public static Path repoRoot() {
        String root = System.getProperty("ga.repoRoot");
        if (root == null) {
            throw new IllegalStateException("system property ga.repoRoot is not set (configured by the Gradle build)");
        }
        return Path.of(root);
    }

    /** 정규 스트림 17건(seq 1~17 — 클래스 설명). */
    public static List<ObjectNode> all() {
        Path dir = repoRoot().resolve("contracts/events/v1/samples");
        List<ObjectNode> out = new ArrayList<>(a1());
        List<Path> singles;
        try (Stream<Path> files = Stream.concat(Files.list(dir.resolve("contract-only")), Files.list(dir.resolve("standalone")))) {
            singles = files.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path p : singles) {
            ObjectNode e = read(p);
            e.put("seq", out.size() + 1);
            out.add(e);
        }
        return out;
    }

    /** 엔진 A-1 스트림(계약 {@code samples/a1/a1_portal_150000_e2.jsonl}, 한 줄 한 Envelope) — 파일 순서·seq 그대로. */
    public static List<ObjectNode> a1() {
        try {
            return Files.readAllLines(repoRoot().resolve("contracts/events/v1/samples/a1/a1_portal_150000_e2.jsonl"), StandardCharsets.UTF_8)
                    .stream().filter(l -> !l.isBlank()).map(l -> (ObjectNode) JSON.readTree(l)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** seq가 {@code seq}인 샘플의 복사본. */
    public static ObjectNode seq(long seq) {
        return all().stream().filter(s -> s.path("seq").asLong() == seq).findFirst().orElseThrow().deepCopy();
    }

    static ObjectNode read(Path p) {
        try {
            return (ObjectNode) JSON.readTree(Files.readString(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code portal-ingest} 시험 자원의 스트림 파일 경로. */
    public static Path stream(String name) {
        return repoRoot().resolve("portal-ingest/src/test/resources/streams").resolve(name);
    }

    /** 같은 샘플을 seq·eventId만 바꿔 늘린다(클라이언트 시험용 — 클라이언트는 이벤트 본문을 보지 않는다). */
    public static ObjectNode withSeq(ObjectNode base, long seq) {
        ObjectNode copy = base.deepCopy();
        copy.put("seq", seq);
        copy.put("eventId", String.format("5eed0000-0000-4000-8000-%012x", 0x100000 + seq));
        return copy;
    }
}

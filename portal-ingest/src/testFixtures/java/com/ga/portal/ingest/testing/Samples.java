package com.ga.portal.ingest.testing;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** 계약 샘플(저장소 {@code contracts/events/v1/samples} — 허구 데이터)과 시험 스트림(이 모듈 시험 자원 {@code streams/}). */
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

    /** 샘플 01~17(seq 순). */
    public static List<ObjectNode> all() {
        Path dir = repoRoot().resolve("contracts/events/v1/samples");
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().map(Samples::read).toList();
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

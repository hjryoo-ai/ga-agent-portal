package com.ga.portal.ingest.feed;

import com.ga.portal.ingest.testing.FeedClientContract;
import com.ga.portal.ingest.testing.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C12: JSONL 파일 어댑터가 같은 피드 계약 스위트를 통과하고, 파일 순서를 그대로(중복·역순 포함) 돌려준다. */
class FileFeedAdapterTest extends FeedClientContract {

    @TempDir
    Path dir;

    private final AtomicInteger files = new AtomicInteger();

    private Path write(String header, List<? extends JsonNode> events) {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        events.forEach(e -> lines.add(Samples.JSON.writeValueAsString(e)));
        Path file = dir.resolve("feed-" + files.incrementAndGet() + ".jsonl");
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    @Override
    protected FeedClient client(String respondedTenant, List<? extends JsonNode> events) {
        Path file = write("{\"tenantId\":\"" + respondedTenant + "\",\"schemaVersion\":1}", events);
        return new FileFeedAdapter(t -> file);
    }

    @Override
    protected FeedClient malformedClient() {
        Path file = write("{\"schemaVersion\":1}", seqs(1));
        return new FileFeedAdapter(t -> file);
    }

    @Test
    void fileOrderIsKeptIncludingLaterDuplicatesAndReplays() {
        FeedClient c = client("GA01", seqs(1, 2, 3, 3, 4, 2));
        assertThat(c.fetch(T, 0).events()).extracting(e -> e.path("seq").asLong()).containsExactly(1L, 2L, 3L, 3L, 4L, 2L);
        assertThat(c.fetch(T, 2).events()).extracting(e -> e.path("seq").asLong()).containsExactly(3L, 3L, 4L, 2L);
        assertThat(c.fetch(T, 4).events()).isEmpty();
    }

    @Test
    void aMissingOrBrokenFileIsUnavailable() throws IOException {
        assertThatThrownBy(() -> new FileFeedAdapter(t -> dir.resolve("none.jsonl")).fetch(T, 0)).isInstanceOf(FeedException.Unavailable.class);
        Path broken = dir.resolve("broken.jsonl");
        Files.writeString(broken, "{\"tenantId\":\"GA01\",\"schemaVersion\":1}\n{\"seq\":1,\"seq\":2}\n");
        assertThatThrownBy(() -> new FileFeedAdapter(t -> broken).fetch(T, 0)).isInstanceOf(FeedException.Unavailable.class);
    }
}

package com.ga.portal.ingest.feed;

import com.ga.platform.canonical.CanonicalizationException;
import com.ga.platform.canonical.Canonicalizer;
import com.ga.platform.core.tenant.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * JSONL 파일 피드(설계서 부록 B 데모·시험). 형식: 첫 줄 머리 {@code {"tenantId": "...", "schemaVersion": 1}}, 다음 줄부터 Envelope 한 줄씩.
 * {@code headSeq} = 파일의 최대 seq. 응답은 파일 순서 그대로 — {@code afterSeq}보다 큰 seq가 처음 나오는 줄부터 최대 {@code limit}줄이고,
 * 그 뒤의 작은 seq(중복·역순)도 그대로 돌려준다(시험 스트림이 그것을 싣는다). 응답 해석은 HTTP와 같은 {@link FeedResponses}.
 */
public final class FileFeedAdapter implements FeedClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Function<TenantId, Path> files;

    public FileFeedAdapter(Function<TenantId, Path> files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    @Override
    public FeedPage fetch(TenantId tenant, long afterSeq, int limit) {
        FeedClient.checkArguments(afterSeq, limit);
        List<String> lines;
        try {
            lines = Files.readAllLines(files.apply(tenant), StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new FeedException.Unavailable("feed file of tenant " + tenant.value() + " unreadable: " + e.getClass().getSimpleName());
        }
        if (lines.isEmpty()) {
            throw new FeedException.Unavailable("feed file of tenant " + tenant.value() + " has no header line");
        }
        List<JsonNode> events = new ArrayList<>();
        JsonNode header;
        try {
            header = Canonicalizer.parseStrict(lines.getFirst());
            for (String line : lines.subList(1, lines.size())) {
                events.add(Canonicalizer.parseStrict(line));
            }
        } catch (CanonicalizationException e) {
            throw new FeedException.Unavailable("feed file of tenant " + tenant.value() + " has a line that is not valid JSON", e);
        }
        long head = events.stream().mapToLong(e -> e.path("seq").asLong()).max().orElse(0);
        if (afterSeq > head) {
            throw new FeedException.AfterBeyondHead(afterSeq);
        }
        int start = 0;
        while (start < events.size() && events.get(start).path("seq").asLong() <= afterSeq) {
            start++;
        }
        List<JsonNode> page = events.subList(start, Math.min(events.size(), start + limit));
        ObjectNode response = header instanceof ObjectNode h ? h.deepCopy() : JSON.createObjectNode();
        ArrayNode array = response.putArray("events");
        page.forEach(array::add);
        response.put("nextSeq", page.isEmpty() ? afterSeq : page.getLast().path("seq").asLong());
        response.put("headSeq", head);
        return FeedResponses.parse(tenant, response);
    }
}

package com.ga.portal.query.rule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 약한 ETag(계획 ⑤ Q4 — DE-16): {@code W/"<seq>.<sync>.<cfg>"}. {@code seq} = 커서 {@code last_seq}, {@code sync} = {@code dataStatus} 한 글자,
 * {@code cfg} = 그 요청이 <b>실제로 읽은</b> 테넌트 설정 값들의 SHA-256 앞 8자(값 사이 {@code U+001F}). 같은 {@code seq}에서도 지연 배지·지급일·임계치가
 * 바뀌면 ETag가 바뀐다. 라인 목록은 뒤에 {@code .<page>.<size>.<필터 해시 8자>}.
 */
public final class Etags {

    private static final String SEPARATOR = "\u001f";

    private Etags() {
    }

    public static String of(long seq, SyncStatusRule.SyncStatus sync, List<String> configRead) {
        return "W/\"" + core(seq, sync, configRead) + "\"";
    }

    public static String ofPage(long seq, SyncStatusRule.SyncStatus sync, List<String> configRead, int page, int size, List<String> filter) {
        return "W/\"" + core(seq, sync, configRead) + "." + page + "." + size + "." + hash8(filter) + "\"";
    }

    private static String core(long seq, SyncStatusRule.SyncStatus sync, List<String> configRead) {
        return seq + "." + sync.tag() + "." + hash8(configRead);
    }

    static String hash8(List<String> values) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join(SEPARATOR, values).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

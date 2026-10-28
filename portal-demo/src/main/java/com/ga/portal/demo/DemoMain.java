package com.ga.portal.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * compose {@code demo} 서비스의 진입점(설계서 §3.2 v1.11). 인자 — {@code --data=<묶음 디렉터리>} {@code --tenant=<테넌트>}
 * {@code --token-file=<서비스 토큰 파일>} {@code --port=<포트>} {@code [--replay=instant|paced:<n>:<ms>]}. 토큰은 파일로만 받는다(인자·환경
 * 변수에 비밀 0 — 지시문 §0). 출력은 준비 한 줄뿐(토큰·요청 없음).
 */
public final class DemoMain {

    private DemoMain() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> a = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (!arg.startsWith("--") || eq < 3) {
                throw new IllegalArgumentException("usage: --data=DIR --tenant=T --token-file=FILE --port=N [--replay=instant|paced:<n>:<ms>]");
            }
            a.put(arg.substring(2, eq), arg.substring(eq + 1));
        }
        DemoData data = DemoData.load(Path.of(required(a, "data")), required(a, "tenant"));
        String token = Files.readString(Path.of(required(a, "token-file")), StandardCharsets.UTF_8).strip();
        if (token.isEmpty()) {
            throw new IllegalStateException("the service token file is empty");
        }
        Replay replay = Replay.parse(a.getOrDefault("replay", "instant"), data.events().size());
        DemoServer server = new DemoServer(data, token, replay, new InetSocketAddress("0.0.0.0", Integer.parseInt(required(a, "port"))));
        System.out.println("demo engine ready: " + data.events().size() + " events, replay " + (replay.paced() ? "paced" : "instant"));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }

    private static String required(Map<String, String> a, String key) {
        String v = a.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("--" + key + " is required");
        }
        return v;
    }
}

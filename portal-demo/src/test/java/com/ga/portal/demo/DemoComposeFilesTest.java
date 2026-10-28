package com.ga.portal.demo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 8 지시문 ②·③·§0(계획 P-14·P-15·P-23·Q5·Q7): compose 데모 파일들이 서로 맞는다 — 실행 전에(Docker 없이) 볼 수 있는 것만.
 * <ul>
 *   <li>렐름 사용자 id = 등록 SQL의 설계사 subject(30), 설계사 = 엔진 데모 스트림의 설계사 집합.</li>
 *   <li>발급자·대상·웹 출처·클라이언트 id가 렐름(KC_HOSTNAME·audience 매퍼·리다이렉트)과 등록 SQL에서 같다. portal-web은 PKCE S256·표준 흐름만.</li>
 *   <li>compose: 이미지 전부 digest 고정, 호스트 포트는 127.0.0.1에만(앱·IdP·Jaeger 셋), 관리 포트 9090은 공개 0, 환경 변수에 비밀 값 0
 *       (비밀은 *_FILE·configtree 경로만), 비밀 생성기는 값을 출력하지 않는다.</li>
 *   <li>렐름 파일에 평문 비밀번호 없음 — 자격 증명은 PBKDF2 해시뿐.</li>
 * </ul>
 */
class DemoComposeFilesTest {

    static final Path ROOT = Path.of(System.getProperty("ga.repoRoot"));
    static final Path DOCKER = ROOT.resolve("docker");

    static JsonNode realm;
    static String seed;
    static String compose;

    @BeforeAll
    static void read() throws IOException {
        realm = DemoData.JSON.readTree(Files.readString(DOCKER.resolve("keycloak/realm.json")));
        seed = Files.readString(DOCKER.resolve("postgres/seed-demo.sql"));
        compose = Files.readString(DOCKER.resolve("compose.yaml"));
    }

    static Set<String> matches(String text, String regex) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void realmUsersAreTheSeededAgentsAndTheStreamsAgents() throws IOException {
        Set<String> realmIds = new TreeSet<>();
        Set<String> realmAgents = new TreeSet<>();
        realm.path("users").forEach(u -> {
            realmIds.add(u.path("id").asString());
            realmAgents.add(u.path("username").asString().toUpperCase(java.util.Locale.ROOT));
        });
        assertThat(realmIds).hasSize(30);
        Pattern row = Pattern.compile("\\('DEMO', '([0-9a-f-]{36})', '(A-[0-9]{4})', 'AGENT', 'ACTIVE'");
        Matcher m = row.matcher(seed);
        Set<String> seededIds = new TreeSet<>();
        Set<String> seededAgents = new TreeSet<>();
        while (m.find()) {
            seededIds.add(m.group(1));
            seededAgents.add(m.group(2));
            assertThat(m.group(1)).as("subject of %s", m.group(2)).endsWith(m.group(2).substring(2));
        }
        assertThat(seededIds).isEqualTo(realmIds);
        assertThat(seededAgents).isEqualTo(realmAgents);
        Set<String> streamAgents;
        try (Stream<String> lines = Files.lines(ROOT.resolve("contracts/shared/ga-commission-engine/samples/demo/demo_ga.jsonl"))) {
            streamAgents = lines.map(l -> DemoData.JSON.readTree(l).path("payload").path("agentId").asString(""))
                    .filter(a -> !a.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
        }
        assertThat(seededAgents).isEqualTo(streamAgents);
        assertThat(seed).contains("('DEMO', 'cli:portal', NULL, 'OPERATOR', 'ACTIVE', NULL,");
    }

    @Test
    void issuerAudienceOriginAndClientAgreeAcrossRealmSeedAndCompose() {
        assertThat(compose).contains("KC_HOSTNAME: http://localhost:8180");
        assertThat(realm.path("realm").asString()).isEqualTo("demo");
        assertThat(seed).contains("'http://localhost:8180/realms/demo', 'portal-demo', 'http://localhost:8080', 'portal-web'");
        JsonNode web = client("portal-web");
        assertThat(web.path("publicClient").asBoolean()).isTrue();
        assertThat(web.path("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(web.path("directAccessGrantsEnabled").asBoolean()).isFalse();
        assertThat(web.path("implicitFlowEnabled").asBoolean()).isFalse();
        assertThat(web.path("attributes").path("pkce.code.challenge.method").asString()).isEqualTo("S256");
        assertThat(strings(web.path("redirectUris"))).containsExactly("http://localhost:8080/auth/callback");
        assertThat(strings(web.path("webOrigins"))).containsExactly("http://localhost:8080");
        for (String id : List.of("portal-web", "k6")) {
            JsonNode mapper = client(id).path("protocolMappers").get(0);
            assertThat(mapper.path("protocolMapper").asString()).as(id).isEqualTo("oidc-audience-mapper");
            assertThat(mapper.path("config").path("included.custom.audience").asString()).as(id).isEqualTo("portal-demo");
        }
        assertThat(client("k6").path("standardFlowEnabled").asBoolean()).isFalse();
        assertThat(realm.path("revokeRefreshToken").asBoolean()).isTrue();
        assertThat(realm.path("refreshTokenMaxReuse").asInt()).isZero();
        assertThat(realm.path("accessTokenLifespan").asInt()).isEqualTo(300);
    }

    @Test
    void theRealmCarriesHashesNotPasswords() {
        realm.path("users").forEach(u -> {
            JsonNode c = u.path("credentials").get(0);
            assertThat(c.has("value")).as("plaintext credential value").isFalse();
            JsonNode secret = DemoData.JSON.readTree(c.path("secretData").asString());
            JsonNode data = DemoData.JSON.readTree(c.path("credentialData").asString());
            assertThat(data.path("algorithm").asString()).isEqualTo("pbkdf2-sha256");
            assertThat(java.util.Base64.getDecoder().decode(secret.path("value").asString())).hasSize(32);
        });
    }

    /** README의 데모 비밀번호(계획 Q7 — 평문은 README 한 곳)가 렐름의 해시와 맞는다 — 설계사 30명 전원(Keycloak pbkdf2-sha256과 같은 계산). */
    @Test
    void theReadmePasswordMatchesEveryRealmHash() throws Exception {
        Matcher m = Pattern.compile("데모 비밀번호: `([^`]+)`").matcher(Files.readString(ROOT.resolve("README.md")));
        assertThat(m.find()).as("README demo password line").isTrue();
        char[] password = m.group(1).toCharArray();
        javax.crypto.SecretKeyFactory pbkdf2 = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        for (JsonNode u : realm.path("users")) {
            JsonNode c = u.path("credentials").get(0);
            JsonNode secret = DemoData.JSON.readTree(c.path("secretData").asString());
            int iterations = DemoData.JSON.readTree(c.path("credentialData").asString()).path("hashIterations").asInt();
            byte[] salt = java.util.Base64.getDecoder().decode(secret.path("salt").asString());
            byte[] expected = java.util.Base64.getDecoder().decode(secret.path("value").asString());
            byte[] actual = pbkdf2.generateSecret(new javax.crypto.spec.PBEKeySpec(password, salt, iterations, 256)).getEncoded();
            assertThat(actual).as(u.path("username").asString()).isEqualTo(expected);
        }
    }

    @Test
    void composePinsImagesBindsLoopbackAndKeepsSecretsOutOfTheEnvironment() throws IOException {
        List<String> images = new ArrayList<>(matches(compose, "(?m)^\\s*image: (?!ga-portal-demo/)(\\S+)"));
        images.addAll(matches(compose, "(?m)^x-postgres-image: &postgres-image (\\S+)"));
        assertThat(images).isNotEmpty().allSatisfy(i -> assertThat(i).as(i).matches("[a-z0-9./_-]+@sha256:[0-9a-f]{64}|\\*postgres-image"));
        assertThat(matches(compose, "(?m)^\\s*- \"([^\"]+)\"$").stream().filter(p -> p.matches(".*:\\d+:\\d+")).toList())
                .containsExactlyInAnyOrder("127.0.0.1:8080:8080", "127.0.0.1:8180:8080", "127.0.0.1:16686:16686");
        assertThat(compose).doesNotContain(":9090\"").doesNotContain("9090:");
        assertThat(matches(compose, "(?m)^\\s+([A-Z_]*(?:PASSWORD|SECRET|TOKEN)[A-Z_]*):")).allSatisfy(k ->
                assertThat(k).as("secret-looking variable %s must point at a file", k).matches(".*_FILE|PORTAL_ENGINE_TOKEN_DIR|PORTAL_POLICY_KEY_SECRET_FILE"));
        String services = compose.substring(compose.indexOf("\nservices:\n"), compose.indexOf("\nvolumes:\n"));
        List<String> names = new ArrayList<>(matches(services, "(?m)^  ([a-z][a-z-]*):$"));
        assertThat(names).containsExactlyInAnyOrder("secrets", "postgres", "keycloak", "demo", "jaeger", "portal", "idp-relay", "seed");
        assertThat(services.split("\n    profiles: \\[demo]\n", -1)).as("every service is in the demo profile (C5)").hasSize(names.size() + 1);
        assertThat(ROOT.resolve(".env")).doesNotExist();
        assertThat(DOCKER.resolve(".env")).doesNotExist();
        String generator = Files.readString(DOCKER.resolve("secrets/generate.sh"));
        assertThat(matches(generator, "(?m)^\\s*echo (.*)$")).allSatisfy(line -> assertThat(line).doesNotContain("$superuser", "$migrator", "$app",
                "$directory", "$issuer", "$token", "$admin"));
        assertThat(Files.readString(DOCKER.resolve("portal.Dockerfile"), StandardCharsets.UTF_8))
                .contains("ADD --checksum=sha256:").doesNotContain("GHP_", "ARG ", "ENV ");
    }

    static JsonNode client(String id) {
        for (JsonNode c : realm.path("clients")) {
            if (c.path("clientId").asString().equals(id)) {
                return c;
            }
        }
        throw new AssertionError("no client " + id);
    }

    static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }
}

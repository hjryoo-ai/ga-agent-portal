package com.ga.portal.app.web;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C11: {@code /api/v1/me/**}의 <b>모든</b> 시험 응답을 계약({@code contracts/api/v1/portal-api.openapi.yaml}, 클래스패스 사본 — portal-ingest 자원)으로 검증한다.
 * {@link ApiIntegrationTest#send}가 응답마다 부른다(가로채기). 200 = 엔드포인트의 응답 스키마 + 약한 ETag·{@code private, no-cache}, 304 = 본문 없음, 400·401·404 =
 * {@code Problem}({@code application/problem+json}). 엔드포인트 × 상태별 검증 수를 센다(보고서 표).
 */
final class ApiContract {

    static final String BASE = "https://ga.example/portal/contracts/api/v1/portal-api.openapi.yaml";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final Pattern ETAG = Pattern.compile("W/\"[0-9]+\\.[OD]\\.[0-9a-f]{8}(\\.[0-9]+\\.[0-9]+\\.[0-9a-f]{8})?\"");

    /** 경로 정규식 → (엔드포인트 템플릿, 응답 스키마). 순서대로 첫 일치. */
    private static final List<Route> ROUTES = List.of(
            new Route("GET /api/v1/me", "^/api/v1/me$", "MeResponse"),
            new Route("GET /api/v1/me/earnings", "^/api/v1/me/earnings$", "EarningsResponse"),
            new Route("GET /api/v1/me/earnings/lines/{calcId}", "^/api/v1/me/earnings/lines/[^/]+$", "LineDetailResponse"),
            new Route("GET /api/v1/me/earnings/{ym}/lines", "^/api/v1/me/earnings/[^/]+/lines$", "LinesResponse"),
            new Route("GET /api/v1/me/earnings/{ym}", "^/api/v1/me/earnings/[^/]+$", "MonthResponse"),
            new Route("GET /api/v1/me/limits", "^/api/v1/me/limits$", "LimitsResponse"),
            new Route("GET /api/v1/me/limits/{ledgerId}", "^/api/v1/me/limits/[^/]+$", "LimitDetailResponse"),
            new Route("GET /api/v1/me/deferrals", "^/api/v1/me/deferrals$", "DeferralsResponse"));

    record Route(String endpoint, String regex, String schema) {
        boolean matches(String path) {
            return Pattern.compile(regex).matcher(path).matches();
        }
    }

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, b -> b.schemas(ApiContract::resolve));
    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private ApiContract() {
    }

    private static String resolve(String iri) {
        if (iri.startsWith(BASE)) {
            return JSON.writeValueAsString(YAML.readTree(read()));
        }
        if (iri.startsWith("https://json-schema.org/")) {
            return null;
        }
        throw new IllegalStateException("schema IRI outside the portal API contract: " + iri);
    }

    static String read() {
        try (InputStream in = ApiContract.class.getClassLoader().getResourceAsStream("contracts/api/v1/portal-api.openapi.yaml")) {
            if (in == null) {
                throw new IllegalStateException("portal-api.openapi.yaml is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Schema schema(String name) {
        return REGISTRY.getSchema(SchemaLocation.of(BASE + "#/components/schemas/" + name));
    }

    static Optional<Route> route(String path) {
        return ROUTES.stream().filter(r -> r.matches(path)).findFirst();
    }

    /** 응답 하나 검증 — {@code /api/v1/me/**}만(시험 진입점 {@code /api/v1/test/**}의 4xx는 Problem만). */
    static void check(String path, HttpResponse<byte[]> r) {
        Optional<Route> route = route(path);
        int status = r.statusCode();
        String key;
        if (status >= 400) {
            assertThat(r.headers().firstValue("content-type")).as("%s %d content type", path, status).contains("application/problem+json");
            assertValid(schema("Problem"), r.body(), path);
            key = route.map(Route::endpoint).orElse("(other " + path.replaceAll("/[^/]*$", "/…") + ")") + " " + status;
        } else if (route.isEmpty()) {
            return;
        } else if (status == 304) {
            assertThat(r.body()).as("%s 304 body", path).isEmpty();
            assertThat(r.headers().firstValue("etag")).as("%s 304 etag", path).hasValueSatisfying(e -> assertThat(e).matches(ETAG));
            key = route.get().endpoint() + " 304";
        } else {
            assertThat(status).as(path).isEqualTo(200);
            assertThat(r.headers().firstValue("etag")).as("%s etag", path).hasValueSatisfying(e -> assertThat(e).matches(ETAG));
            assertThat(r.headers().allValues("cache-control")).as("%s cache-control", path).containsExactly("private, no-cache");
            assertValid(schema(route.get().schema()), r.body(), path);
            key = route.get().endpoint() + " 200";
        }
        COUNTS.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    private static void assertValid(Schema schema, byte[] body, String path) {
        List<Error> errors = schema.validate(JSON.readTree(body));
        assertThat(errors).as("contract violations for %s", path).isEmpty();
    }

    /** 엔드포인트 × 상태 → 검증한 응답 수(이 JVM에서). */
    static Map<String, Integer> counts() {
        Map<String, Integer> out = new TreeMap<>();
        COUNTS.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}

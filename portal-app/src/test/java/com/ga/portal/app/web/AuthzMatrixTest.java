package com.ga.portal.app.web;

import com.ga.portal.domain.Role;
import com.ga.portal.query.MeQueries;
import com.ga.portal.domain.access.ApiAuthorization;
import com.ga.portal.domain.access.UseCaseEntry;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C5(지시문 D-11): {@code docs/authz-matrix.md}의 블록(정본) ↔ {@link ApiAuthorization} ↔ 컨트롤러 {@code @GetMapping} ↔ {@code @UseCaseEntry} 값 — 양방향.
 * 열 {@code AGENT}·{@code MANAGER}는 인가 표, {@code OPERATOR}·{@code NO_LINK}·{@code REVOKED}는 구조상 언제나 404({@code Caller}가 만들어지지 않는다 —
 * {@code CallerArgumentResolver}) — 문서가 그 칸을 SELF로 바꾸면 실패한다.
 */
class AuthzMatrixTest {

    static final Path DOC = Path.of(System.getProperty("ga.repoRoot"), "docs", "authz-matrix.md");
    static final Pattern BLOCK = Pattern.compile("```authz-matrix\\n(.*?)\\n```", Pattern.DOTALL);
    static final String HEADER = "endpoint,AGENT,MANAGER,OPERATOR,NO_LINK,REVOKED";

    static Map<String, List<String>> parse(String markdown) {
        Matcher m = BLOCK.matcher(markdown);
        if (!m.find()) {
            throw new IllegalStateException("no authz-matrix block");
        }
        int start = m.start();
        if (m.find()) {
            throw new IllegalStateException("more than one authz-matrix block");
        }
        m.find(start);
        List<String> lines = List.of(m.group(1).split("\n"));
        if (!lines.getFirst().equals(HEADER)) {
            throw new IllegalStateException("header must be " + HEADER);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            if (cells.length != 6 || Arrays.stream(cells).skip(1).anyMatch(c -> !c.equals("SELF") && !c.equals("404"))) {
                throw new IllegalStateException("bad row: " + line);
            }
            if (out.put(cells[0], List.of(cells).subList(1, 6)) != null) {
                throw new IllegalStateException("duplicate row: " + cells[0]);
            }
        }
        return out;
    }

    /** 코드가 말하는 표: 역할 셋은 인가 표에서, 링크 없음·해제·OPERATOR는 구조상 404. */
    static Map<String, List<String>> fromCode() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        ApiAuthorization.matrix().forEach((endpoint, roles) -> out.put(endpoint, List.of(
                roles.contains(Role.AGENT) ? "SELF" : "404", roles.contains(Role.MANAGER) ? "SELF" : "404", "404", "404", "404")));
        return out;
    }

    static Set<String> controllerTemplates() {
        return Arrays.stream(MeController.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(GetMapping.class))
                .flatMap(m -> Arrays.stream(m.getAnnotation(GetMapping.class).value()).map(v -> "GET " + v))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static Set<String> useCaseEntries() {
        return Arrays.stream(MeQueries.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(UseCaseEntry.class))
                .map(m -> m.getAnnotation(UseCaseEntry.class).value()).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void theDocumentTheAuthorizationTableTheControllerAndTheEntriesAgree() throws Exception {
        Map<String, List<String>> doc = parse(Files.readString(DOC, StandardCharsets.UTF_8));
        assertThat(doc).containsExactlyEntriesOf(fromCode());
        assertThat(new TreeSet<>(doc.keySet())).isEqualTo(controllerTemplates()).isEqualTo(useCaseEntries());
        assertThat(controllerTemplates()).hasSize(8);
        // OPERATOR 열은 인가 표가 무엇을 말하든 404 — 표가 OPERATOR를 허용하면 문서·구조와 어긋난다
        assertThat(ApiAuthorization.matrix().values()).allSatisfy(roles -> assertThat(roles).doesNotContain(Role.OPERATOR));
    }

    @Test
    void eachEntryIsCalledByExactlyOneControllerMethodWithTheSameTemplate() {
        // 컨트롤러 메서드 이름 = 유스케이스 메서드 이름이고 템플릿이 같다(어느 진입점이 어느 경로인지가 두 곳에서 같다)
        Map<String, String> entryByName = new LinkedHashMap<>();
        for (Method m : MeQueries.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(UseCaseEntry.class)) {
                entryByName.put(m.getName(), m.getAnnotation(UseCaseEntry.class).value());
            }
        }
        for (Method m : MeController.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(GetMapping.class)) {
                assertThat(entryByName).as(m.getName()).containsEntry(m.getName(), "GET " + m.getAnnotation(GetMapping.class).value()[0]);
            }
        }
    }

    @Test
    void aChangedCellOrAMissingRowIsDetected() throws Exception {
        String text = Files.readString(DOC, StandardCharsets.UTF_8);
        assertThat(parse(text.replace("GET /api/v1/me/limits,SELF,404,", "GET /api/v1/me/limits,SELF,SELF,"))).isNotEqualTo(fromCode());
        assertThat(parse(text.replace("GET /api/v1/me/deferrals,SELF,404,404,404,404\n", ""))).isNotEqualTo(fromCode());
        assertThatThrownBy(() -> parse("```authz-matrix\nendpoint,AGENT\n```")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> parse(text.replace(",SELF,404,404,404,404\nGET /api/v1/me/earnings,", ",SELF,404,404,404,ALLOW\nGET /api/v1/me/earnings,")))
                .isInstanceOf(IllegalStateException.class);
    }
}

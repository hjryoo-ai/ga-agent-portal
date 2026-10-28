package com.ga.portal.app.web;

import com.ga.portal.app.screen.ScreenConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포털 Phase 8 계획 Q2·P-12(조건 A): 인증 없는 경로는 닫힌 목록이다 — {@code docs/authz-matrix.md}의 {@code public-paths} 블록(정본) ↔ 나머지 체인이
 * {@code permitAll}로 여는 {@link ApiSecurityConfiguration#PUBLIC_PATHS} 양방향. {@code /api/**}·화면 경로({@code /auth/callback} 포함)는 이 목록에 없다.
 */
class PublicPathsTest {

    static final Pattern BLOCK = Pattern.compile("```public-paths\\n(.*?)\\n```", Pattern.DOTALL);

    static List<String> parse(String markdown) {
        Matcher m = BLOCK.matcher(markdown);
        if (!m.find()) {
            throw new IllegalStateException("no public-paths block");
        }
        List<String> paths = List.of(m.group(1).split("\n"));
        if (m.find()) {
            throw new IllegalStateException("more than one public-paths block");
        }
        if (paths.stream().distinct().count() != paths.size() || paths.stream().anyMatch(p -> !p.startsWith("/") || p.contains(" "))) {
            throw new IllegalStateException("bad public-paths block: " + paths);
        }
        return paths;
    }

    @Test
    void theDocumentBlockIsTheSecurityConfigurationBothWays() throws IOException {
        List<String> doc = parse(Files.readString(AuthzMatrixTest.DOC, StandardCharsets.UTF_8));
        assertThat(ApiSecurityConfiguration.PUBLIC_PATHS).containsExactlyInAnyOrderElementsOf(doc);
        assertThat(doc).containsExactlyInAnyOrderElementsOf(ApiSecurityConfiguration.PUBLIC_PATHS);
        assertThat(doc).contains(AuthConfigHandler.PATH);
    }

    @Test
    void noPublicPathIsAnApiOrScreenPath() {
        assertThat(ApiSecurityConfiguration.PUBLIC_PATHS).noneMatch(p -> p.startsWith("/api/"));
        assertThat(ApiSecurityConfiguration.PUBLIC_PATHS).doesNotContain("/auth/callback").doesNotContainAnyElementsOf(ScreenConfiguration.SCREEN_PATHS);
        assertThat(ScreenConfiguration.SCREEN_PATHS).contains("/auth/callback");
    }

    @Test
    void theParserRejectsMalformedBlocks() {
        assertThatThrownBy(() -> parse("nothing")).hasMessageContaining("no public-paths");
        assertThatThrownBy(() -> parse("```public-paths\n/a\n```\n```public-paths\n/b\n```")).hasMessageContaining("more than one");
        assertThatThrownBy(() -> parse("```public-paths\n/a\n/a\n```")).hasMessageContaining("bad");
        assertThat(parse("```public-paths\n/a\n/b\n```")).containsExactly("/a", "/b");
    }
}

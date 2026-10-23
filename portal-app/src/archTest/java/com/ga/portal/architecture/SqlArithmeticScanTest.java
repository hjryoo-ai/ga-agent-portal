package com.ga.portal.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C3(포털 Phase 5 계획 Q16): 포털 제품 SQL(마이그레이션과 자바 문자열의 문장)에 산술 {@code *}·{@code /}·{@code %}가 없다 — 허용 목록(파일, 연산 조각, 사유)의
 * 조각 안에 있는 것만 예외다. {@code count(*)}·{@code select *}·{@code 별칭.*}·{@code returning *}은 산술이 아니다. 문장 분리·주석 제거·문자열 비우기는
 * {@link SqlTenantScanner}와 같다(그래서 정규식·LIKE 패턴 속 기호는 보지 않는다). 시나리오의 금액 곱셈은 SQL이 아니라 자바 한 메서드
 * ({@code MultiplicationScanTest})다.
 */
class SqlArithmeticScanTest {

    record Allowed(String source, String fragment, String reason) {
    }

    static final List<Allowed> ALLOWLIST = List.of(
            new Allowed("portal-infra/src/main/java/com/ga/portal/infra/persistence/SyncStatusRepository.java", "round(t.limit_warn_pct * 100)",
                    "경고 임계치 % → 정수 bp(Phase 3 — 단계 판정이 정수 비교가 되게). 금액이 아니다"),
            new Allowed("portal-infra/src/main/java/com/ga/portal/infra/persistence/SyncStatusRepository.java", "round(t.limit_crit_pct * 100)",
                    "위험 임계치 % → 정수 bp. 금액이 아니다"),
            new Allowed("portal-infra/src/main/java/com/ga/portal/infra/persistence/TenantRepository.java", "round(limit_warn_pct * 100)",
                    "테넌트 설정 읽기의 같은 변환(% → bp). 금액이 아니다"),
            new Allowed("portal-infra/src/main/java/com/ga/portal/infra/persistence/TenantRepository.java", "round(limit_crit_pct * 100)",
                    "테넌트 설정 읽기의 같은 변환(% → bp). 금액이 아니다"));

    private static final Pattern NOT_ARITHMETIC = Pattern.compile("count\\(\\s*\\*\\s*\\)|\\bselect\\s+(?:distinct\\s+)?\\*|[a-z_][a-z0-9_]*\\.\\*|\\breturning\\s+\\*");
    private static final Pattern OPERATOR = Pattern.compile("[*/%]");
    private static final Path ROOT = Path.of(System.getProperty("ga.repoRoot"));

    record Site(String source, String statement, int at) {
        String context() {
            return statement.substring(Math.max(0, at - 30), Math.min(statement.length(), at + 30));
        }
    }

    private static List<Site> sites;
    private static int statements;

    @BeforeAll
    static void scan() throws IOException {
        sites = new ArrayList<>();
        statements = 0;
        for (String module : TenantPredicateScanTest.MODULES) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(module).resolve("src").resolve("main"))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".sql") || f.toString().endsWith(".java")).sorted().toList()) {
                    String text = Files.readString(p, StandardCharsets.UTF_8);
                    List<String> sql = p.toString().endsWith(".sql") ? List.of(text) : SqlTenantScanner.sqlLiteralsInJava(text);
                    for (String literal : sql) {
                        for (String statement : SqlTenantScanner.statementsOf(literal)) {
                            statements++;
                            sites.addAll(arithmetic(ROOT.relativize(p).toString().replace('\\', '/'), statement));
                        }
                    }
                }
            }
        }
    }

    static List<Site> arithmetic(String source, String statement) {
        StringBuilder blanked = new StringBuilder(statement);
        Matcher skip = NOT_ARITHMETIC.matcher(statement);
        while (skip.find()) {
            for (int i = skip.start(); i < skip.end(); i++) {
                blanked.setCharAt(i, ' ');
            }
        }
        List<Site> out = new ArrayList<>();
        Matcher op = OPERATOR.matcher(blanked);
        while (op.find()) {
            out.add(new Site(source, statement, op.start()));
        }
        return out;
    }

    static boolean allowed(Site s) {
        return ALLOWLIST.stream().anyMatch(a -> {
            if (!a.source().equals(s.source())) {
                return false;
            }
            int from = 0;
            int i;
            while ((i = s.statement().indexOf(a.fragment(), from)) >= 0) {
                if (s.at() >= i && s.at() < i + a.fragment().length()) {
                    return true;
                }
                from = i + 1;
            }
            return false;
        });
    }

    @Test
    void productionSqlHasNoArithmeticOutsideTheAllowlist() {
        assertThat(statements).as("statements scanned (not vacuous)").isGreaterThan(80);
        assertThat(sites.stream().filter(s -> !allowed(s)).map(s -> s.source() + ": …" + s.context() + "…").toList())
                .as("SQL arithmetic outside the allowlist").isEmpty();
        assertThat(ALLOWLIST).allSatisfy(a -> {
            assertThat(a.reason()).isNotBlank();
            assertThat(sites).as("stale allowlist entry %s", a.fragment())
                    .anyMatch(s -> s.source().equals(a.source()) && allowed(s) && s.statement().contains(a.fragment()));
        });
    }

    @Test
    void theScannerSeparatesArithmeticFromStars() {
        assertThat(arithmetic("x", "select count(*), l.* from earnings_line l where amount > 0")).isEmpty();
        assertThat(arithmetic("x", "select * from t returning *")).isEmpty();
        assertThat(arithmetic("x", "select sum(amount) * :p / 100 from deferral_item")).hasSize(2);
        assertThat(arithmetic("x", "select amount % 7 from t")).hasSize(1);
        assertThat(SqlTenantScanner.statementsOf("select 'a*b/c%' as s from t -- x * y")).singleElement()
                .satisfies(st -> assertThat(arithmetic("x", st)).isEmpty());
    }

}

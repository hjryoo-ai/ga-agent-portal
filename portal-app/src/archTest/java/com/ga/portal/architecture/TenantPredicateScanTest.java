package com.ga.portal.architecture;

import com.ga.portal.architecture.SqlTenantScanner.Dialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C6(지시문 v1.2 §6, v1.0 §6 원문 — 계획 승인 §4): 모든 포털 모듈의 main 소스를 스캔한다 — 마이그레이션 {@code .sql}(D1: DML도 대상)과
 * 자바 문자열 SQL. {@code flyway_schema_history}를 뺀 테넌트 테이블 접근 문장은 {@code tenant_id} 조건(또는 컬럼 지정)을 가져야 하고,
 * 자바 저장소 SQL은 {@code :tenantId} 바인드까지(D3 — INSERT는 tenant_id 자리 값, 나머지는 {@code tenant_id = :tenantId}).
 * {@code tenant}도 제외하지 않는다(D2).
 */
class TenantPredicateScanTest {

    /**
     * 허용 목록: 공백 정규화·소문자화된 문장 → 사유. 비어 있는 상태로 시작한다.
     * 항목을 추가하려면 사유를 반드시 적는다(빈 사유·쓰이지 않는 항목은 {@link #allowlistEntriesHaveReasonsAndAreUsed()}가 거부).
     */
    static final Map<String, String> ALLOWLIST = Map.of();

    static final List<String> MODULES = List.of(
            "portal-domain", "portal-projection", "portal-ingest", "portal-query", "portal-sim", "portal-recon",
            "portal-infra", "portal-app", "portal-demo");

    private static final Path ROOT = Path.of(System.getProperty("ga.repoRoot"));

    private static List<Path> sqlFiles;
    private static List<Path> javaFiles;
    private static Set<String> knownTables;

    @BeforeAll
    static void collect() throws IOException {
        sqlFiles = walk(".sql");
        javaFiles = walk(".java");
        knownTables = SqlTenantScanner.tablesDefinedIn(sqlFiles.stream().map(TenantPredicateScanTest::read).toList());
    }

    private static List<Path> walk(String suffix) throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            try (Stream<Path> s = Files.walk(ROOT.resolve(module).resolve("src").resolve("main"))) {
                s.filter(p -> p.toString().endsWith(suffix)).sorted().forEach(out::add);
            }
        }
        return out;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<SqlTenantScanner.Violation> scanAll() {
        List<SqlTenantScanner.Violation> all = new ArrayList<>();
        for (Path p : sqlFiles) {
            all.addAll(SqlTenantScanner.scan(ROOT.relativize(p).toString(), read(p), knownTables, Dialect.MIGRATION));
        }
        for (Path p : javaFiles) {
            for (String literal : SqlTenantScanner.sqlLiteralsInJava(read(p))) {
                all.addAll(SqlTenantScanner.scan(ROOT.relativize(p).toString(), literal, knownTables, Dialect.JAVA));
            }
        }
        return all;
    }

    @Test
    void scanCoversMigrationsAndRepositories() {
        assertThat(sqlFiles).extracting(p -> p.getFileName().toString()).containsExactly("V1__init.sql", "V2__rls.sql");
        assertThat(knownTables).hasSize(17).contains("tenant", "earnings_line", "ingest_cursor", "audit_access");
        long javaSqlCount = javaFiles.stream().mapToLong(p -> SqlTenantScanner.sqlLiteralsInJava(read(p)).size()).sum();
        assertThat(javaSqlCount).as("repository SQL literals found (TenantRepository 1, IngestCursorRepository 2)").isEqualTo(3);
    }

    @Test
    void everyTenantTableAccessHasTenantPredicate() {
        List<SqlTenantScanner.Violation> violations = scanAll().stream()
                .filter(v -> !ALLOWLIST.containsKey(v.statement()))
                .toList();
        assertThat(violations).as("tenant_id 없는 테이블 접근 문장(불변 규약 2)").isEmpty();
    }

    @Test
    void allowlistEntriesHaveReasonsAndAreUsed() {
        List<String> flagged = scanAll().stream().map(SqlTenantScanner.Violation::statement).toList();
        ALLOWLIST.forEach((statement, reason) -> {
            assertThat(reason).as("사유 없는 허용 목록 항목: %s", statement).isNotBlank();
            assertThat(flagged).as("쓰이지 않는 허용 목록 항목(제거할 것): %s", statement).contains(statement);
        });
    }

    // ------------------------------------------------------------------ 스캐너 자체의 음성·양성 시험(영구 보존)

    private static final Set<String> TABLES = Set.of("tenant", "earnings_line", "deferral_item", "ingest_cursor");

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM earnings_line WHERE close_ym = :ym",
            "SELECT name FROM tenant",
            "SELECT l.amount FROM earnings_line l JOIN deferral_item d ON d.source_calc_id = l.calc_id",
            "UPDATE deferral_item SET status = 'HELD' WHERE schedule_id = :id",
            "DELETE FROM earnings_line WHERE calc_id = :id",
            "INSERT INTO earnings_line (calc_id, amount) VALUES (:id, :amount)",
            "INSERT INTO earnings_line VALUES (:id, :amount)",
            "SELECT * FROM earnings_line WHERE tenant_id = ?",
            "SELECT * FROM earnings_line WHERE tenant_id = 'T1'",
            "INSERT INTO ingest_cursor (tenant_id, last_seq) VALUES (:other, 0)",
            "INSERT INTO ingest_cursor (tenant_id, last_seq) VALUES ('T1', 0)",
    })
    void javaScannerFlagsStatementsWithoutTheBoundTenantPredicate(String sql) {
        assertThat(SqlTenantScanner.scan("inline", sql, TABLES, Dialect.JAVA)).as(sql).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM earnings_line WHERE tenant_id = :tenantId AND close_ym = :ym",
            "SELECT name FROM tenant WHERE tenant_id = :tenantId",
            "UPDATE deferral_item SET status = 'HELD' WHERE tenant_id = :tenantId AND schedule_id = :id",
            "INSERT INTO ingest_cursor (tenant_id, last_seq) VALUES (:tenantId, 0)",
            "INSERT INTO ingest_cursor (last_seq, tenant_id, state) VALUES (0, :tenantId, coalesce(:s, 'RUNNING'))",
            "INSERT INTO ingest_cursor (tenant_id, last_seq) VALUES (:tenantId, 0) ON CONFLICT (tenant_id) DO UPDATE SET last_seq = EXCLUDED.last_seq WHERE ingest_cursor.tenant_id = :tenantId",
            "SELECT count(*) FROM flyway_schema_history",
            "-- SELECT * FROM earnings_line\nSELECT 1",
            "SELECT 'FROM earnings_line' AS text",
    })
    void javaScannerAcceptsBoundTenantScopedOrIrrelevantStatements(String sql) {
        assertThat(SqlTenantScanner.scan("inline", sql, TABLES, Dialect.JAVA)).as(sql).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE earnings_line SET product_key = 'X'",
            "DELETE FROM deferral_item WHERE status = 'CANCELLED'",
            "INSERT INTO deferral_item (schedule_id) VALUES ('S1')",
            "CREATE TABLE shadow (seq BIGINT)",
            "CREATE INDEX ix_bad ON earnings_line (close_ym)",
            "DO $body$ BEGIN PERFORM 1 FROM earnings_line WHERE close_ym = '202608'; END $body$",
    })
    void migrationScannerFlagsDmlWithoutTenantAndStructureWithoutTenantColumn(String sql) {
        assertThat(SqlTenantScanner.scan("inline.sql", sql, TABLES, Dialect.MIGRATION)).as(sql).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE earnings_line SET product_key = 'X' WHERE tenant_id = 'T1'",
            "DELETE FROM deferral_item WHERE tenant_id IN ('T1', 'T2') AND status = 'CANCELLED'",
            "INSERT INTO deferral_item (tenant_id, schedule_id) VALUES ('T1', 'S1')",
            "CREATE INDEX ix_ok ON earnings_line (tenant_id, close_ym)",
            "ALTER TABLE earnings_line ENABLE ROW LEVEL SECURITY",
            "REVOKE ALL ON TABLE earnings_line FROM PUBLIC",
    })
    void migrationScannerAcceptsTenantScopedDmlAndDdl(String sql) {
        assertThat(SqlTenantScanner.scan("inline.sql", sql, TABLES, Dialect.MIGRATION)).as(sql).isEmpty();
    }

    @Test
    void javaLiteralExtractionFindsTextBlocksAndStrings() {
        String java = """
                class R {
                    // "SELECT * FROM earnings_line" in a comment is ignored
                    String a = "SELECT * FROM earnings_line WHERE x = 1";
                    String b = \"""
                            DELETE FROM deferral_item
                             WHERE tenant_id = :tenantId
                            \""";
                    char q = '"';
                    String c = "not sql";
                }
                """;
        assertThat(SqlTenantScanner.sqlLiteralsInJava(java)).hasSize(2);
        assertThat(SqlTenantScanner.sqlLiteralsInJava(java).stream()
                .flatMap(l -> SqlTenantScanner.scan("R.java", l, TABLES, Dialect.JAVA).stream())).hasSize(1);
    }
}

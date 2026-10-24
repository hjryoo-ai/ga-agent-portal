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

    /** 허용 항목: 그 파일(저장소 상대 경로)의 그 문장(공백 정규화·소문자화)만 — 같은 문장이 다른 파일에 있으면 허용되지 않는다. */
    record AllowedStatement(String source, String statement, String reason) {
    }

    /**
     * 허용 목록 — Phase 0~2는 비어 있었다. 항목을 추가하려면 사유를 반드시 적는다(빈 사유·쓰이지 않는 항목은 {@link #allowlistEntriesHaveReasonsAndAreUsed()}가
     * 거부). 항목은 아키텍처 규칙의 직접 DB 접근 허용 목록(닫힌 FQN)에 있는 클래스의 문장뿐이다.
     */
    static final List<AllowedStatement> ALLOWLIST = List.of(
            new AllowedStatement("portal-infra/src/main/java/com/ga/portal/infra/directory/IssuerDirectoryReader.java",
                    "select tenant_id, oidc_issuer, oidc_audience from tenant where oidc_issuer = :issuer",
                    "발급자 → 테넌트(Phase 3 계획 승인 Q1): 테넌트를 바인딩하기 전의 조회. 전용 롤 portal_issuer가 tenant의 세 열만 읽는다 — "
                            + "ArchitectureRulesTest.PORTAL_DB_ALLOWLIST의 항목, IssuerDirectoryIT"));

    private static boolean allowed(SqlTenantScanner.Violation v) {
        return ALLOWLIST.stream().anyMatch(a -> a.source().equals(v.source()) && a.statement().equals(v.statement()));
    }

    static final List<String> MODULES = List.of(
            "portal-domain", "portal-projection", "portal-ingest", "portal-query", "portal-sim", "portal-inbox", "portal-recon",
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
        assertThat(sqlFiles).extracting(p -> p.getFileName().toString()).containsExactly("V1__init.sql", "V2__rls.sql", "V3__ingest_payload_hash.sql", "V4__projection.sql",
                "V5__query_api.sql", "V6__simulation_log.sql", "V7__inbox.sql");
        assertThat(knownTables).hasSize(17).contains("tenant", "earnings_line", "ingest_cursor", "audit_access");
        long javaSqlCount = javaFiles.stream().mapToLong(p -> SqlTenantScanner.sqlLiteralsInJava(read(p)).size()).sum();
        assertThat(javaSqlCount).as("repository SQL literals found (TenantRepository 1, IngestCursorRepository 3, IngestEventRepository 4, "
                + "IngestLockRepository 1, AuditAccessRepository 2; Phase 2: AgentProfile 2, EarningsLine 3, EarningsMonth 2, PolicyLimit 5, "
                + "DeferralItem 3, ClawbackItem 2, PolicyFlag 2, ReconResult 1, ViewReset 8 DELETE + 3 invariant queries; Phase 3: IngestCursor +2 caught_up_at, IssuerDirectoryReader 1, IdentityLink 1, "
                + "AuditAccess +1 denial, AgentQuery 16, SyncStatus 1; Phase 5: AgentQuery +3 monthDetail·deferralPolicies·policies, "
                + "SimulationLog 4, SimulationLock 1; Phase 6: Notification 4, Inquiry 7)")
                .isEqualTo(11 + 20 + 11 + 2 + 3 + 17 + 3 + 4 + 1 + 4 + 7);
    }

    @Test
    void everyTenantTableAccessHasTenantPredicate() {
        List<SqlTenantScanner.Violation> violations = scanAll().stream()
                .filter(v -> !allowed(v))
                .toList();
        assertThat(violations).as("tenant_id 없는 테이블 접근 문장(불변 규약 2)").isEmpty();
    }

    @Test
    void allowlistEntriesHaveReasonsAndAreUsed() {
        List<SqlTenantScanner.Violation> flagged = scanAll();
        ALLOWLIST.forEach(a -> {
            assertThat(a.reason()).as("사유 없는 허용 목록 항목: %s", a.statement()).isNotBlank();
            assertThat(flagged).as("쓰이지 않는 허용 목록 항목(제거할 것): %s", a.statement())
                    .anyMatch(v -> v.source().equals(a.source()) && v.statement().equals(a.statement()));
        });
        assertThat(ALLOWLIST).extracting(AllowedStatement::source).as("Phase 3: the issuer directory reader only")
                .containsExactly("portal-infra/src/main/java/com/ga/portal/infra/directory/IssuerDirectoryReader.java");
    }

    // ------------------------------------------------------------------ 두 번째 술어(포털 Phase 3 지시문 D-4)

    /**
     * 조회 API 저장소 — 닫힌 목록(저장소 상대 경로). 이 파일들의 <b>모든</b> 문장은 설계사 표 참조마다 {@code agent_id = :agentId}를 가진다. 허용 목록은
     * 비어 있다({@link #AGENT_ALLOWLIST}).
     */
    static final List<String> AGENT_SCOPED_SOURCES = List.of("portal-infra/src/main/java/com/ga/portal/infra/persistence/AgentQueryRepository.java");

    /** 두 번째 술어의 허용 목록(파일, 문장, 사유) — 비어 있다. */
    static final List<AllowedStatement> AGENT_ALLOWLIST = List.of();

    private static Set<String> agentTables() {
        return SqlTenantScanner.tablesWithColumn(sqlFiles.stream().map(TenantPredicateScanTest::read).toList(), "agent_id");
    }

    @Test
    void everyAgentScopedStatementHasAnAgentPredicatePerAgentTable() {
        Set<String> agentTables = agentTables();
        assertThat(agentTables).contains("earnings_line", "earnings_month", "policy_limit", "policy_flag", "deferral_item", "clawback_item",
                "agent_profile", "notification", "inquiry").doesNotContain("policy_limit_history", "tenant", "ingest_cursor");
        List<SqlTenantScanner.Violation> violations = new ArrayList<>();
        int statements = 0;
        for (String source : AGENT_SCOPED_SOURCES) {
            Path file = ROOT.resolve(source);
            assertThat(file).as("agent-scoped source exists").isRegularFile();
            for (String literal : SqlTenantScanner.sqlLiteralsInJava(read(file))) {
                statements++;
                violations.addAll(SqlTenantScanner.scanAgentPredicate(source, literal, agentTables));
            }
        }
        assertThat(statements).as("AgentQueryRepository statements (not vacuous — Phase 5: +monthDetail, +deferralPolicies, +policies)").isEqualTo(19);
        assertThat(violations).as("agent-scoped statements without agent_id = :agentId per agent table (D-4)").isEmpty();
        assertThat(AGENT_ALLOWLIST).as("second-predicate allowlist (Phase 3: empty)").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM earnings_line WHERE tenant_id = :tenantId AND calc_id = :calcId",
            "SELECT l.* FROM earnings_line l JOIN deferral_item d ON d.source_calc_id = l.calc_id WHERE l.tenant_id = :tenantId AND l.agent_id = :agentId",
            "SELECT (SELECT min(r.calc_id) FROM earnings_line r WHERE r.tenant_id = :tenantId) FROM earnings_line l WHERE l.tenant_id = :tenantId AND l.agent_id = :agentId",
            "SELECT * FROM earnings_line WHERE tenant_id = :tenantId AND agent_id = :other",
            "SELECT * FROM earnings_line WHERE tenant_id = :tenantId AND agent_id IS NOT NULL",
            "SELECT h.* FROM policy_limit_history h WHERE h.tenant_id = :tenantId AND h.ledger_id = :ledgerId",
    })
    void theSecondPredicateFlagsMissingAgentPredicates(String sql) {
        Set<String> agentTables = Set.of("earnings_line", "deferral_item", "policy_limit");
        assertThat(SqlTenantScanner.scanAgentPredicate("inline", sql, agentTables)).as(sql).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM earnings_line WHERE tenant_id = :tenantId AND agent_id = :agentId AND calc_id = :calcId",
            "SELECT (SELECT min(r.calc_id) FROM earnings_line r WHERE r.tenant_id = :tenantId AND r.agent_id = :agentId) FROM earnings_line l "
                    + "WHERE l.tenant_id = :tenantId AND l.agent_id = :agentId",
            "SELECT h.* FROM policy_limit_history h JOIN policy_limit p ON p.ledger_id = h.ledger_id WHERE h.tenant_id = :tenantId AND p.agent_id = :agentId",
    })
    void theSecondPredicateAcceptsOnePredicatePerAgentTable(String sql) {
        Set<String> agentTables = Set.of("earnings_line", "deferral_item", "policy_limit");
        assertThat(SqlTenantScanner.scanAgentPredicate("inline", sql, agentTables)).as(sql).isEmpty();
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
            // 계획 승인 Q6: 표에 닿지 않는 문장(함수 호출뿐)은 대상이 아니다 — 허용 목록 없이 통과한다(IngestLockRepository)
            "SELECT pg_try_advisory_xact_lock(hashtext('portal.ingest'), hashtext(:tenantId)) AS locked",
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

    /**
     * 포털 Phase 5: {@code simulation_log} 저장소의 모든 문장은 그 설계사를 묶는다({@code :agentId} — 넣기는 값, 읽기·갱신은 {@code agent_id = :agentId}). 두 번째
     * 술어 스캔({@link #AGENT_SCOPED_SOURCES})은 FROM/JOIN 참조로 세므로 INSERT·UPDATE를 볼 수 없다 — 여기서 문장마다 본다.
     */
    @Test
    void everySimulationLogStatementIsBoundToTheAgent() {
        String source = "portal-infra/src/main/java/com/ga/portal/infra/persistence/SimulationLogRepository.java";
        List<String> statements = SqlTenantScanner.sqlLiteralsInJava(read(ROOT.resolve(source))).stream()
                .flatMap(l -> SqlTenantScanner.statementsOf(l).stream()).toList();
        assertThat(statements).hasSize(4);
        assertThat(statements).allSatisfy(s -> assertThat(s).contains("tenant_id").contains(":agentid"));
        assertThat(statements.stream().filter(s -> !s.startsWith("insert"))).allSatisfy(s -> assertThat(s).contains("agent_id = :agentid"));
        assertThat(statements.stream().filter(s -> s.startsWith("update"))).hasSize(2)
                .allSatisfy(s -> assertThat(s).endsWith("and sim_id = :simid and status = ''"));   // 문자열은 스캐너가 비운다 — REQUESTED 행만
    }

    /**
     * 포털 Phase 6: 알림·문의 저장소(설계사 범위 — 운영 CLI의 테넌트 전체 문장은 따로)의 모든 문장은 그 설계사를 묶는다 — 넣기는 값({@code :agentId}), 읽기·갱신은
     * {@code agent_id = :agentId}, 잠금은 둘째 키. 두 번째 술어 스캔({@link #AGENT_SCOPED_SOURCES})은 FROM/JOIN 참조로 세므로 INSERT·UPDATE·잠금을 볼 수 없다 —
     * {@code simulation_log}(Phase 5)와 같이 여기서 문장마다 보고, SELECT에는 그 스캔(설계사 표마다 술어)도 돌린다.
     */
    @Test
    void everyInboxStatementIsBoundToTheAgent() {
        Set<String> agentTables = agentTables();
        for (String name : List.of("NotificationRepository", "InquiryRepository")) {
            String source = "portal-infra/src/main/java/com/ga/portal/infra/persistence/" + name + ".java";
            List<String> literals = SqlTenantScanner.sqlLiteralsInJava(read(ROOT.resolve(source)));
            List<SqlTenantScanner.Violation> violations = new ArrayList<>();
            literals.stream().filter(l -> l.strip().regionMatches(true, 0, "SELECT", 0, 6) && !l.contains("pg_advisory_xact_lock"))
                    .forEach(l -> violations.addAll(SqlTenantScanner.scanAgentPredicate(source, l, agentTables)));
            assertThat(violations).as(name + " selects without agent_id = :agentId per agent table").isEmpty();
            List<String> statements = literals.stream().flatMap(l -> SqlTenantScanner.statementsOf(l).stream()).toList();
            assertThat(statements).as(name).hasSize(name.startsWith("Notification") ? 4 : 7);
            assertThat(statements).as(name).allSatisfy(s -> assertThat(s).contains(":tenantid").contains(":agentid"));
            assertThat(statements.stream().filter(s -> !s.startsWith("insert") && !s.contains("pg_advisory_xact_lock")))
                    .as(name).allSatisfy(s -> assertThat(s).contains("agent_id = :agentid"));
            assertThat(statements).as(name + " deletes nothing").noneMatch(s -> s.startsWith("delete"));
        }
    }
}

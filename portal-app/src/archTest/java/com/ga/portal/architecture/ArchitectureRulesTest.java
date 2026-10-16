package com.ga.portal.architecture;

import com.ga.platform.core.arch.ArchRules;
import com.ga.platform.core.arch.ArchRules.Allowed;
import com.ga.platform.core.arch.ArchRules.Layer;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * C5(지시문 v1.2 §6): 아키텍처 규칙 (a)~(h). 규칙 정의는 platform-core {@link ArchRules}(ga-disclosure와 공유)이고, 이 클래스는 포털의
 * 패키지·허용 목록으로 규칙을 구성한다. 검사 대상은 포털 9개 모듈의 운영 클래스와 플랫폼 0.2.0 jar의 클래스({@code com.ga..})이며
 * 테스트·테스트 픽스처·이 소스셋은 제외한다. (a)~(d)는 v1.0 §6 원문(계획 승인 §4)을 포함하고 더 넓다.
 *
 * <p><b>허용 목록</b>은 패턴이 아니라 FQN 열거이고 항목마다 사유를 적는다. 포털의 직접 DB 접근 허용 목록은 Phase 0에서 비어 있다
 * (불변 규약 2 — 닫힌 FQN + 전용 롤 + 테넌트 데이터 미접근). 플랫폼 클래스는 jar SHA-256으로 고정된 공급물이라 따로 적는다.
 * {@link #allowlistsHaveNoStaleEntries()}가 폐기 항목을 잡는다.
 */
class ArchitectureRulesTest {

    static final String P = "com.ga.portal.";

    // ------------------------------------------------------------------ 허용 목록 (FQN, 사유)

    /** DB 접근의 정식 경로: 이 클래스의 하위 클래스만 JDBC 결과 타입(RowMapper의 ResultSet 등)을 쓸 수 있다. */
    static final Allowed REPOSITORY_BASE = new Allowed(TenantScopedRepository.class.getName(),
            "모든 저장소의 기반 클래스 — :tenantId 강제·컨텍스트 주입·바인더 트랜잭션 대조 후에만 JdbcClient를 호출한다(platform-spring 0.2.0)");

    /** 원시 JDBC 진입점을 만지는 플랫폼 클래스(포털이 아니라 jar SHA-256으로 고정된 플랫폼 0.2.0). */
    static final List<Allowed> PLATFORM_DB_CLASSES = List.of(
            new Allowed("com.ga.platform.spring.jdbc.TenantJdbcGateway",
                    "DataSource로 JdbcClient를 만들어 저장소 기반 클래스에만 건네는 봉투(하위 저장소에는 노출하지 않는다)"),
            new Allowed("com.ga.platform.spring.jdbc.TenantSessionBinder",
                    "트랜잭션 시작 시 set_config('app.tenant_id', ?, true)로 RLS 세션 값을 넣는 트랜잭션 매니저"),
            new Allowed("com.ga.platform.spring.jdbc.PlatformJdbcAutoConfiguration",
                    "애플리케이션 DataSource로 위 두 빈을 조립하는 자동 구성"),
            new Allowed("com.ga.platform.spring.jdbc.TenantDirectoryReader",
                    "테넌트 ID 목록 전용 롤용 리더 — 포털 Phase 0은 쓰지 않는다(플랫폼 jar에 있어 규칙이 본다)"));

    /** 포털의 직접 DB 접근 허용 목록 — Phase 0은 비어 있다(지시문 v1.2 불변 규약 2). */
    static final List<Allowed> PORTAL_DB_ALLOWLIST = List.of();

    /** 연결·문장 수준 JDBC 타입 — (g) 플랫폼 클래스와 포털 허용 목록만. 결과 타입(ResultSet)은 (d)가 저장소 하위 클래스까지 허용한다. */
    static final Set<String> CONNECTION_LEVEL_JDBC = Set.of(
            "javax.sql.DataSource", "java.sql.DriverManager", "java.sql.Connection", "java.sql.Statement",
            "java.sql.PreparedStatement", "java.sql.CallableStatement");

    private static JavaClasses classes;

    /** 테스트 소스셋(test·integrationTest·archTest·testFixtures) 산출물을 제외한다. */
    static final ImportOption PRODUCTION_ONLY = (Location location) ->
            new ImportOption.DoNotIncludeTests().includes(location)
                    && !location.contains("/archTest/")
                    && !location.contains("/integrationTest/")
                    && !location.contains("/testFixtures/")
                    && !location.contains("-test-fixtures");

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter().withImportOption(PRODUCTION_ONLY).importPackages("com.ga");
    }

    private static List<Allowed> dbAnchors() {
        List<Allowed> anchors = new ArrayList<>(PLATFORM_DB_CLASSES);
        anchors.addAll(PORTAL_DB_ALLOWLIST);
        return anchors;
    }

    @Test
    void importsEveryModuleAndThePlatform() {
        // 규칙이 빈 집합에 대해 통과하는 일이 없도록, 대표 클래스·패키지가 실제로 들어왔는지 확인한다.
        for (String module : List.of("domain", "projection", "ingest", "query", "sim", "recon", "infra", "app", "demo")) {
            assertThat(classes.containPackage(P + module)).as(module).isTrue();
        }
        assertThat(classes.contain(P + "infra.persistence.TenantRepository")).isTrue();
        assertThat(classes.contain(P + "domain.CommType")).isTrue();
        assertThat(classes.contain(TenantScopedRepository.class)).isTrue();
        assertThat(classes.contain("com.ga.platform.core.money.Won")).isTrue();
        assertThat(classes.stream().map(JavaClass::getName))
                .noneMatch(n -> n.contains(".architecture.") || n.endsWith("IT") || n.endsWith("Test") || n.contains(".testing."));
    }

    // (a) 의존 방향: app → query/ingest/sim/recon → projection → domain → platform-core. infra는 app만(어댑터), demo는 app만.
    @Test
    void layeredModuleDependencies() {
        ArchRules.layeredDependencies(
                Layer.of("PlatformCore", "com.ga.platform.core..",
                        "PlatformSpring", "PlatformCanonical", "Domain", "Projection", "Ingest", "Query", "Sim", "Recon", "Infra", "App", "Demo"),
                Layer.of("PlatformSpring", "com.ga.platform.spring..", "Infra", "App"),
                Layer.of("PlatformCanonical", "com.ga.platform.canonical..", "Ingest", "Query", "Sim", "Recon", "Infra", "App"),
                Layer.of("Domain", P + "domain..", "Projection", "Ingest", "Query", "Sim", "Recon", "Infra", "App", "Demo"),
                Layer.of("Projection", P + "projection..", "Ingest", "Query", "Sim", "Recon", "App"),
                Layer.of("Ingest", P + "ingest..", "App"),
                Layer.of("Query", P + "query..", "App"),
                Layer.of("Sim", P + "sim..", "App"),
                Layer.of("Recon", P + "recon..", "App"),
                Layer.of("Infra", P + "infra..", "App"),
                Layer.of("Demo", P + "demo..", "App"),
                Layer.of("App", P + "app.."))
                .check(classes);
    }

    // (b) Spring·DB 무의존: portal-domain·portal-projection, 그리고 그 둘이 기대는 platform-core(의존성 그래프로 확인).
    //     domain·projection이 의존할 수 있는 플랫폼 모듈은 platform-core뿐이다.
    @Test
    void springAndDbFreeModules() {
        ArchRules.springFreeModules(List.of(P + "domain..", P + "projection..", "com.ga.platform.core.."), List.of()).check(classes);
        noClasses().that().resideInAnyPackage(P + "domain..", P + "projection..")
                .should().dependOnClassesThat().resideInAnyPackage("org.flywaydb..", "com.ga.platform.spring..", "com.ga.platform.canonical..")
                .allowEmptyShould(true)
                .as("portal-domain·portal-projection depend only on platform-core (no Flyway, platform-spring, platform-canonical)")
                .check(classes);
    }

    // (c) double/float/Double/Float 금지(전 모듈) — 필드·인자·반환과 부동소수 값을 만드는 호출·참조
    @Test
    void noDoubleOrFloat() {
        ArchRules.noDoubleOrFloat().check(classes);
    }

    // (d) DB 접근은 TenantScopedRepository 하위 클래스만. 원시 진입점(DataSource·JdbcClient·JdbcTemplate…)은 플랫폼 클래스와 포털 허용 목록(빈)만.
    @Test
    void dbAccessOnlyViaTenantScopedRepository() {
        ArchRules.dbAccessOnlyVia(REPOSITORY_BASE.fqn(), dbAnchors()).check(classes);
    }

    // (e) platform-spring 참조는 infra·app만
    @Test
    void platformSpringOnlyInInfraAndApp() {
        noClasses().that().resideInAPackage("com.ga.portal..")
                .and().resideOutsideOfPackages(P + "infra..", P + "app..")
                .should().dependOnClassesThat().resideInAPackage("com.ga.platform.spring..")
                .allowEmptyShould(true)
                .as("com.ga.platform.spring is referenced only in portal-infra and portal-app")
                .check(classes);
    }

    // (f) 플랫폼 core 타입과 단순 이름이 같은 포털 클래스 0(Won·Ratio·Ym·TenantId·AgentId·TenantContext…를 다시 만들지 않는다)
    @Test
    void noPortalClassSharesASimpleNameWithAPlatformCoreType() {
        Set<String> platformNames = classes.stream()
                .filter(c -> c.getPackageName().startsWith("com.ga.platform.core") && c.isTopLevelClass())
                .map(JavaClass::getSimpleName).collect(Collectors.toSet());
        assertThat(platformNames).contains("Won", "Ratio", "Ym", "TenantId", "AgentId", "TenantContext");
        List<String> clashes = classes.stream()
                .filter(c -> c.getPackageName().startsWith(P) && c.isTopLevelClass() && platformNames.contains(c.getSimpleName()))
                .map(JavaClass::getName).sorted().toList();
        assertThat(clashes).as("portal classes named like a com.ga.platform.core type").isEmpty();
    }

    // (g) 연결·문장 수준 JDBC 타입(DataSource·DriverManager·Connection·Statement 계열)은 플랫폼 클래스와 포털 허용 목록(빈)만.
    @Test
    void connectionLevelJdbcTypesOnlyInPlatformClasses() {
        Set<String> anchors = dbAnchors().stream().map(Allowed::fqn).collect(Collectors.toSet());
        noClasses().that(com.tngtech.archunit.base.DescribedPredicate.describe("are not an allowlisted DB class",
                        (JavaClass c) -> anchors.stream().noneMatch(a -> c.getName().equals(a) || c.getName().startsWith(a + "$"))))
                .should().dependOnClassesThat(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "connection-level JDBC types " + CONNECTION_LEVEL_JDBC, (JavaClass t) -> CONNECTION_LEVEL_JDBC.contains(t.getName())))
                .allowEmptyShould(true)
                .as("java.sql/javax.sql connection-level types are referenced only by " + anchors)
                .check(classes);
    }

    // (h) BigDecimal 금지 — 전 포털 모듈(계획 승인 Q2. 지시문은 domain·projection). DB NUMERIC은 SQL에서 정수로 읽는다.
    @Test
    void noBigDecimalInAnyPortalModule() {
        noClasses().that().resideInAPackage("com.ga.portal..")
                .should().dependOnClassesThat().belongToAnyOf(BigDecimal.class)
                .allowEmptyShould(true)
                .as("java.math.BigDecimal is not referenced in any portal module (Q2)")
                .check(classes);
    }

    @Test
    void allowlistsHaveNoStaleEntries() {
        List<Allowed> all = new ArrayList<>(dbAnchors());
        all.add(REPOSITORY_BASE);
        assertThat(all).allSatisfy(a -> assertThat(a.reason()).as("reason for %s", a.fqn()).isNotBlank());
        assertThat(ArchRules.staleClasses(classes, all)).as("stale class entries").isEmpty();
        assertThat(PORTAL_DB_ALLOWLIST).as("portal direct-DB allowlist (Phase 0: empty)").isEmpty();
    }
}

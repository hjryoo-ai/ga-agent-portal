package com.ga.portal.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * C5(계획 승인 Q8 — ga-disclosure 6A와 같은 방식): 조회 API 계층 규칙.
 * <ol type="a">
 *   <li>모든 {@code @RestController} 처리 메서드는 {@code Caller} 인자를 받고 {@code @UseCaseEntry} 메서드를 <b>정확히 하나</b> 부른다.</li>
 *   <li>{@code Jwt}를 참조하는 포털 클래스는 닫힌 목록({@link #JWT_READERS}) — 토큰 클레임은 검증기 밖으로 나가지 않는다. {@code JwtAuthenticationToken}은 아무도.
 *       컨트롤러는 Spring Security·{@code Principal}을 보지 않는다.</li>
 *   <li>{@code portal-query}의 공개 메서드 중 {@code Caller}를 받는 것은 {@code @UseCaseEntry}뿐(닫힌 예외: {@link #CALLER_TAKING_HELPERS}).</li>
 *   <li>모든 {@code @UseCaseEntry} 메서드는 {@code QueryAuthorization.require}를 직접 부른다.</li>
 *   <li>{@code Caller}의 생성자는 {@code CallerArgumentResolver}만 부른다.</li>
 *   <li>조회 경로({@code portal-query}·{@code app.query})가 닿는 저장소는 설계사 술어 저장소 둘뿐 — 다른 뷰 저장소로 우회하지 않는다.</li>
 * </ol>
 */
class ApiLayerRulesTest {

    static final String P = "com.ga.portal.";
    static final String CALLER = P + "query.Caller";
    static final String USE_CASE_ENTRY = P + "query.UseCaseEntry";
    static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    static final Set<String> MAPPINGS = Set.of("org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping", "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping", "org.springframework.web.bind.annotation.RequestMapping");

    /** {@code Jwt}를 볼 수 있는 클래스(FQN, 사유) — 닫힌 목록. */
    static final List<ArchRulesAllowed> JWT_READERS = List.of(new ArchRulesAllowed(P + "app.web.IssuerTenantResolver",
            "검사 2~8을 하는 검증기 — 토큰에서 iss·sub·aud·exp/nbf·tenant_id만 읽고 (테넌트, sub)만 남긴다(계획 ③)"));

    /** {@code Caller}를 받지만 진입점이 아닌 공개 메서드(FQN#이름, 사유) — 닫힌 목록. */
    static final List<ArchRulesAllowed> CALLER_TAKING_HELPERS = List.of(new ArchRulesAllowed(P + "query.QueryAuthorization#require",
            "진입점이 첫 줄에서 부르는 역할 확인 자체"));

    /** 조회 경로가 쓸 수 있는 저장소. */
    static final Set<String> QUERY_REPOSITORIES = Set.of(P + "infra.persistence.AgentQueryRepository", P + "infra.persistence.SyncStatusRepository");

    record ArchRulesAllowed(String fqn, String reason) {
    }

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter().withImportOption(ArchitectureRulesTest.PRODUCTION_ONLY).importPackages("com.ga.portal");
    }

    static boolean isHandler(JavaMethod m) {
        return MAPPINGS.stream().anyMatch(m::isAnnotatedWith);
    }

    static List<JavaMethod> useCaseEntriesCalledBy(JavaMethod m) {
        List<JavaMethod> out = new ArrayList<>();
        for (JavaMethodCall call : m.getMethodCallsFromSelf()) {
            call.getTarget().resolveMember().filter(t -> t.isAnnotatedWith(USE_CASE_ENTRY)).ifPresent(t -> {
                if (!out.contains(t)) {
                    out.add(t);
                }
            });
        }
        return out;
    }

    @Test
    void a_everyHandlerTakesACallerAndCallsExactlyOneUseCaseEntry() {
        List<JavaClass> controllers = classes.stream().filter(c -> c.isAnnotatedWith(REST_CONTROLLER)).toList();
        assertThat(controllers).extracting(JavaClass::getName).containsExactly(P + "app.web.MeController");
        List<String> problems = new ArrayList<>();
        int handlers = 0;
        for (JavaClass c : controllers) {
            for (JavaMethod m : c.getMethods()) {
                if (!isHandler(m)) {
                    continue;
                }
                handlers++;
                if (m.getRawParameterTypes().stream().noneMatch(t -> t.getName().equals(CALLER))) {
                    problems.add(m.getFullName() + " has no Caller parameter");
                }
                int entries = useCaseEntriesCalledBy(m).size();
                if (entries != 1) {
                    problems.add(m.getFullName() + " calls " + entries + " @UseCaseEntry methods");
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(handlers).as("handlers (= rows of docs/authz-matrix.md)").isEqualTo(8);
    }

    @Test
    void b_onlyTheVerifierSeesTheJwtAndControllersSeeNoSecurityTypes() {
        Set<String> readers = JWT_READERS.stream().map(ArchRulesAllowed::fqn).collect(Collectors.toSet());
        noClasses().that(DescribedPredicate.describe("are not " + readers, (JavaClass c) -> !readers.contains(c.getName())))
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.security.oauth2.jwt.Jwt")
                .as("only " + readers + " reference Jwt").check(classes);
        noClasses().should().dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
                .allowEmptyShould(true).as("no portal class references JwtAuthenticationToken").check(classes);
        noClasses().that().areAnnotatedWith(REST_CONTROLLER)
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.security.Principal")
                .as("controllers do not read the security context or a Principal").check(classes);
        assertThat(classes.contain(readers.iterator().next())).as("the allowlisted reader exists").isTrue();
    }

    @Test
    void c_onlyUseCaseEntriesTakeACallerInPortalQuery() {
        Set<String> helpers = CALLER_TAKING_HELPERS.stream().map(ArchRulesAllowed::fqn).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        int entries = 0;
        for (JavaClass c : classes) {
            if (!c.getPackageName().startsWith(P + "query")) {
                continue;
            }
            for (JavaMethod m : c.getMethods()) {
                boolean takesCaller = m.getRawParameterTypes().stream().anyMatch(t -> t.getName().equals(CALLER));
                if (!takesCaller || !m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
                    continue;
                }
                if (m.isAnnotatedWith(USE_CASE_ENTRY)) {
                    entries++;
                } else if (!helpers.contains(c.getName() + "#" + m.getName())) {
                    problems.add(m.getFullName() + " takes a Caller but is not a @UseCaseEntry");
                }
            }
        }
        assertThat(entries).isEqualTo(8);
        assertThat(problems).isEmpty();
        assertThat(helpers).allSatisfy(h -> assertThat(classes.get(h.substring(0, h.indexOf('#'))).getMethods())
                .as("stale helper %s", h).anyMatch(m -> m.getName().equals(h.substring(h.indexOf('#') + 1))));
    }

    @Test
    void d_everyUseCaseEntryCallsTheAuthorizationCheck() {
        List<String> missing = new ArrayList<>();
        for (JavaClass c : classes) {
            for (JavaMethod m : c.getMethods()) {
                if (m.isAnnotatedWith(USE_CASE_ENTRY) && m.getMethodCallsFromSelf().stream()
                        .noneMatch(call -> call.getTargetOwner().getName().equals(P + "query.QueryAuthorization") && call.getName().equals("require"))) {
                    missing.add(m.getFullName());
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void e_onlyTheArgumentResolverConstructsACaller() {
        noClasses().that().doNotHaveFullyQualifiedName(P + "app.web.CallerArgumentResolver")
                .should().callConstructorWhere(DescribedPredicate.describe("Caller constructor",
                        call -> call.getTargetOwner().getName().equals(CALLER)))
                .as("Caller is built only from identity_link (CallerArgumentResolver)").check(classes);
    }

    @Test
    void f_theQueryPathReadsViewsOnlyThroughTheAgentScopedRepositories() {
        noClasses().that().resideInAnyPackage(P + "query..", P + "app.query..", P + "app.web..")
                .should().dependOnClassesThat(DescribedPredicate.describe("view repositories other than " + QUERY_REPOSITORIES,
                        (JavaClass t) -> t.getPackageName().equals(P + "infra.persistence") && t.getSimpleName().endsWith("Repository")
                                && !QUERY_REPOSITORIES.contains(t.getName())
                                && !t.getName().equals(P + "infra.persistence.AuditAccessRepository")
                                && !t.getName().equals(P + "infra.persistence.IdentityLinkRepository")))
                .as("the query path reads views through AgentQueryRepository/SyncStatusRepository only (audit and identity are not views)")
                .check(classes);
        assertThat(QUERY_REPOSITORIES).allSatisfy(r -> assertThat(classes.contain(r)).as(r).isTrue());
    }
}

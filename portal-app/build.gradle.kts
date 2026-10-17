// Spring Boot 조립(설계서 §3.3). Phase 0: 진입점 + /actuator/health(DB 헬스 비활성 — 포털 Phase 8에서 전용 롤로 복원).
// integrationTest: Testcontainers PostgreSQL로 부팅 스모크(Flyway는 portal_migrator, 데이터소스는 portal_app).
// archTest: 전 모듈 아키텍처 규칙(ArchUnit) + SQL 테넌트 조건 스캔(5단계).
plugins {
    alias(libs.plugins.spring.boot)
    `jvm-test-suite`
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":portal-ingest"))
    implementation(project(":portal-query"))
    implementation(project(":portal-sim"))
    implementation(project(":portal-recon"))
    implementation(project(":portal-infra"))
    implementation(libs.platform.spring)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    // 조회 API(Phase 3): /api/** 리소스 서버 — 발급자별 디코더, 같은 바이트 거부(계획 ②·③)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
}

val portalModules = listOf(
    ":portal-domain", ":portal-projection", ":portal-ingest", ":portal-query", ":portal-sim", ":portal-recon",
    ":portal-infra", ":portal-demo",
)

testing {
    suites {
        register<JvmTestSuite>("archTest") {
            dependencies {
                implementation(project())
                portalModules.forEach { implementation(project(it)) }
                implementation(libs.platform.core)
                implementation(libs.platform.spring)
                // archunit의 전이 의존(slf4j-api)까지 Boot BOM 버전으로 고정한다.
                implementation(enforcedPlatform(libs.spring.boot.bom))
                implementation(libs.archunit)
                implementation(libs.junit.jupiter)
                implementation(libs.assertj.core)
                runtimeOnly(libs.junit.platform.launcher)
            }
            targets.all {
                testTask.configure {
                    // TenantPredicateScanTest가 모든 모듈의 main 소스(.java 문자열 SQL·마이그레이션 .sql)를 읽는다(verifyTestInputs)
                    listOf("portal-domain", "portal-projection", "portal-ingest", "portal-query", "portal-sim", "portal-recon",
                        "portal-infra", "portal-app", "portal-demo").forEach { m ->
                        inputs.dir(rootProject.layout.projectDirectory.dir("$m/src/main")).withPathSensitivity(PathSensitivity.RELATIVE)
                    }
                }
            }
        }
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())
                implementation(testFixtures(project(":portal-infra")))
                implementation(testFixtures(project(":portal-ingest")))
                implementation(testFixtures(project(":portal-projection")))
                implementation(project(":portal-ingest"))
                implementation(project(":portal-infra"))
                implementation(project(":portal-domain"))
                implementation(project(":portal-query"))
                implementation(libs.platform.spring)
                implementation(platform(libs.spring.boot.bom))
                implementation(libs.spring.boot.starter.test)
                implementation(libs.spring.boot.starter.actuator)
                // 시험 발급기(TestIssuer): RSA 키·JWKS·서명(계획 ⑦ — Keycloak 컨테이너는 Phase 8)
                implementation(libs.nimbus.jose.jwt)
                implementation(libs.spring.boot.starter.security.oauth2.resource.server)
                // 조회 API 응답 계약 검증(ApiContract — portal-api.openapi.yaml, C11): 계약 사본은 portal-ingest 자원(contracts/api/v1/**)
                implementation(libs.json.schema.validator)
                implementation(libs.jackson.dataformat.yaml)
                implementation(libs.junit.jupiter)
                implementation(libs.assertj.core)
                runtimeOnly(libs.junit.platform.launcher)
            }
            targets.all {
                testTask.configure {
                    shouldRunAfter(tasks.test)
                    // 소스셋 밖에서 읽는 파일(verifyTestInputs): 하네스가 읽는 롤 초기화 스크립트·버전 카탈로그(이미지 digest)
                    inputs.file(rootProject.layout.projectDirectory.file("docker/postgres/init-roles.sql")).withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.file(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")).withPathSensitivity(PathSensitivity.RELATIVE)
                    // 인제스트 IT: 계약 샘플과 시험 스트림(portal-ingest 시험 자원)을 읽는다
                    inputs.dir(rootProject.layout.projectDirectory.dir("contracts/events/v1/samples")).withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.dir(rootProject.layout.projectDirectory.dir("portal-ingest/src/test/resources/streams")).withPathSensitivity(PathSensitivity.RELATIVE)
                    // 프로젝션 IT(Phase 2): 골든 스트림·골든 뷰(portal-projection 시험 자원 — 시험 픽스처 GoldenViews·Envelopes가 읽는다)
                    inputs.dir(rootProject.layout.projectDirectory.dir("portal-projection/src/test/resources/golden-streams")).withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.dir(rootProject.layout.projectDirectory.dir("portal-projection/src/test/resources/golden-views")).withPathSensitivity(PathSensitivity.RELATIVE)
                    // 조회 API IT(Phase 3): 응답 계약(ApiContract — 클래스패스 사본의 원천)
                    inputs.file(rootProject.layout.projectDirectory.file("contracts/api/v1/portal-api.openapi.yaml")).withPathSensitivity(PathSensitivity.RELATIVE)
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("archTest"), testing.suites.named("integrationTest"))
}

// AuthzMatrixTest(Phase 3 C5)가 인가 매트릭스 정본을 읽는다(verifyTestInputs)
tasks.test {
    inputs.file(rootProject.layout.projectDirectory.file("docs/authz-matrix.md")).withPathSensitivity(PathSensitivity.RELATIVE)
}

// ---------------------------------------------------------------------------------------------
// 실 엔진 E2E(포털 Phase 3 지시문 D-16) — 정규 빌드(check) 밖. e2e/engine-feed/run.sh가 부른다(사용자가 손으로 실행).
//  runTestIssuer: 인프로세스 시험 발급기(TestIssuer)를 띄워 토큰·발급자 URL을 상태 디렉터리에 쓴다(종료될 때까지 JWKS를 낸다)
//  e2eCheck     : 뷰 9표 = 골든 a1, GET /api/v1/me/earnings/202608 계약 통과 → RESULT.md
// ---------------------------------------------------------------------------------------------
val integrationTestSourceSet = sourceSets.named("integrationTest")
tasks.register<JavaExec>("runTestIssuer") {
    group = "e2e"
    description = "Runs the in-process test OIDC issuer for e2e/engine-feed (writes token and issuer URL to the given state dir)."
    classpath = integrationTestSourceSet.get().runtimeClasspath
    mainClass = "com.ga.portal.app.web.TestIssuerMain"
}
tasks.register<JavaExec>("e2eCheck") {
    group = "e2e"
    description = "Checks the e2e/engine-feed run: 9 views = golden a1 and one /api/v1/me response against the contract; writes RESULT.md."
    classpath = integrationTestSourceSet.get().runtimeClasspath
    mainClass = "com.ga.portal.app.web.EngineFeedE2eCheck"
    systemProperty("ga.repoRoot", rootDir.absolutePath)
}

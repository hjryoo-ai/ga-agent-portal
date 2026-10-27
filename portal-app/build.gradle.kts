import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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
    implementation(project(":portal-inbox"))
    implementation(project(":portal-recon"))
    implementation(project(":portal-infra"))
    implementation(libs.platform.spring)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    // 트레이스(Phase 7 Q6): Observation → OpenTelemetry. 내보내기 없음 — 스팬은 프로세스 안에서만(Phase 8에서 OTLP)
    implementation(libs.spring.boot.micrometer.tracing.opentelemetry)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    // 조회 API(Phase 3): /api/** 리소스 서버 — 발급자별 디코더, 같은 바이트 거부(계획 ②·③)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    // 설계사 화면(포털 Phase 4 — 단일 아티팩트): web 모듈 jar의 classpath:/portal-web/(Vite 산출물)
    runtimeOnly(project(":web"))
}

val portalModules = listOf(
    ":portal-domain", ":portal-projection", ":portal-ingest", ":portal-query", ":portal-sim", ":portal-inbox", ":portal-recon",
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
                    listOf("portal-domain", "portal-projection", "portal-ingest", "portal-query", "portal-sim", "portal-inbox", "portal-recon",
                        "portal-infra", "portal-app", "portal-demo").forEach { m ->
                        inputs.dir(rootProject.layout.projectDirectory.dir("$m/src/main")).withPathSensitivity(PathSensitivity.RELATIVE)
                    }
                    // MetricsCatalogTest(포털 Phase 7 C8)가 지표 목록 정본을 읽는다
                    inputs.file(rootProject.layout.projectDirectory.file("docs/지표.md")).withPathSensitivity(PathSensitivity.RELATIVE)
                }
            }
        }
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())
                implementation(testFixtures(project(":portal-infra")))
                implementation(testFixtures(project(":portal-ingest")))
                implementation(testFixtures(project(":portal-projection")))
                implementation(testFixtures(project(":portal-sim")))
                implementation(testFixtures(project(":portal-recon")))
                implementation(project(":portal-sim"))
                implementation(project(":portal-recon"))
                implementation(project(":portal-inbox"))
                implementation(project(":portal-ingest"))
                implementation(project(":portal-infra"))
                implementation(project(":portal-domain"))
                implementation(project(":portal-query"))
                implementation(libs.platform.spring)
                implementation(platform(libs.spring.boot.bom))
                implementation(libs.spring.boot.starter.test)
                implementation(libs.spring.boot.starter.actuator)
                implementation(libs.opentelemetry.sdk.testing)
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
                    // 화면 서빙 IT(Phase 4): 화면 경로 목록(WebScreensIT가 서버 목록과 대조)
                    inputs.file(rootProject.layout.projectDirectory.file("web/src/routes.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
                    // 화면 E2E 하네스(WebE2eHarness — 같은 소스셋)가 읽는 포털 손 스트림
                    inputs.dir(rootProject.layout.projectDirectory.dir("web/e2e/streams")).withPathSensitivity(PathSensitivity.RELATIVE)
                    inputs.dir(rootProject.layout.projectDirectory.dir("contracts/shared/ga-commission-engine")).withPathSensitivity(PathSensitivity.RELATIVE)
                    // 대사 스케줄러(포털 Phase 7 P-11): 앱 문맥을 띄우는 시험에서 1분 틱이 회차를 쓰지 않게 끈다 — 회차는 시험이 직접 부른다
                    environment("PORTAL_RECON_SCHEDULER_ENABLED", "false")
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("archTest"), testing.suites.named("integrationTest"))
}

// 단일 아티팩트(포털 Phase 4 지시문 ⑧·C12): bootJar 안에 web jar가 있고 그 안에 화면(portal-web/index.html)과 자원이 있어야 한다.
val verifyBootJarScreens = tasks.register("verifyBootJarScreens") {
    group = "verification"
    description = "Fails unless the boot jar carries the web jar with portal-web/index.html and its assets."
    val bootJar = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").flatMap { it.archiveFile }
    inputs.file(bootJar)
    outputs.upToDateWhen { false }
    doLast {
        val jar = bootJar.get().asFile
        ZipFile(jar).use { outer ->
            val web = outer.entries().asSequence().singleOrNull { it.name.matches(Regex("BOOT-INF/lib/web-[^/]+\\.jar")) }
                ?: throw GradleException("${jar.name}: no BOOT-INF/lib/web-*.jar — the screens are not in the single artifact")
            val names = ZipInputStream(outer.getInputStream(web)).use { z ->
                generateSequence { z.nextEntry }.map { it.name }.toList()
            }
            val assets = names.count { it.startsWith("portal-web/assets/") && it.endsWith(".js") }
            if ("portal-web/index.html" !in names || assets == 0) {
                throw GradleException("${web.name}: portal-web/index.html or its scripts are missing ($assets scripts)")
            }
            if (names.any { it.startsWith("portal-web/.vite/") || it.endsWith(".map") }) {
                throw GradleException("${web.name}: build metadata or source maps in the served files")
            }
            logger.lifecycle("verifyBootJarScreens: ${jar.name} ⊃ ${web.name.substringAfterLast('/')} ⊃ portal-web/index.html + $assets scripts")
        }
    }
}
tasks.named("check") { dependsOn(verifyBootJarScreens) }

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
// 화면 E2E(포털 Phase 4 계획 ⑨): web/e2e/run.mjs가 하네스(WebE2eHarness — 통합 시험 소스셋)를 java -cp로 띄운다. 그 클래스패스를 파일로 넘긴다
// (명령 인자에 긴 경로 목록·비밀 없음). :web:e2e가 부른다 — check 밖.
tasks.register("webE2eHarnessClasspath") {
    group = "e2e"
    description = "Writes the integrationTest runtime classpath for the web E2E harness."
    val classpath = integrationTestSourceSet.map { it.runtimeClasspath }
    val out = layout.buildDirectory.file("web-e2e/harness.classpath")
    inputs.files(classpath)
    outputs.file(out)
    doLast { out.get().asFile.writeText(classpath.get().files.joinToString(File.pathSeparator)) }
}
tasks.register<JavaExec>("e2eCheck") {
    group = "e2e"
    description = "Checks the e2e/engine-feed run: 9 views = golden a1 and one /api/v1/me response against the contract; writes RESULT.md."
    classpath = integrationTestSourceSet.get().runtimeClasspath
    mainClass = "com.ga.portal.app.web.EngineFeedE2eCheck"
    systemProperty("ga.repoRoot", rootDir.absolutePath)
}

// PostgreSQL 어댑터(platform-spring의 TenantScopedRepository 하위 저장소), Flyway 마이그레이션·RLS(설계서 §3.3·§5).
// infra는 app에서만 조립된다(의존 방향 — ArchUnit).
// 통합 테스트(integrationTest)는 Testcontainers PostgreSQL. Docker가 없으면 실패한다(스킵 금지).
plugins {
    `java-test-fixtures`
    `jvm-test-suite`
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":portal-domain"))
    implementation(libs.platform.spring)
    implementation(libs.spring.context)
    runtimeOnly(libs.postgresql)

    // PostgresHarness: 컨테이너 1회 기동 → init-roles.sql → portal_migrator로 Flyway → portal_app 데이터소스
    testFixturesApi(platform(libs.spring.boot.bom))
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers)
    testFixturesApi(libs.postgresql)
    testFixturesImplementation(libs.spring.jdbc)
    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.flyway.postgresql)

    // RlsSettingNameTest(C10)·StatusEnumDdlTest(Q9)는 클래스패스의 마이그레이션과 플랫폼 상수·포털 열거를 대조한다(DB 불요)
    testImplementation(libs.platform.spring)
    testImplementation(libs.spring.context)
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())
                implementation(testFixtures(project()))
                implementation(project(":portal-domain"))
                implementation(libs.platform.spring)
                implementation(platform(libs.spring.boot.bom))
                implementation(libs.spring.jdbc)
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
                    // 같은 JVM에서 컨테이너를 재사용한다(PostgresHarness 싱글턴).
                    maxParallelForks = 1
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

// StatusEnumDdlTest(Q9)가 설계서 §5 주석을 읽는다. 단위 시험 클래스패스에도 시험 픽스처(PostgresHarness — 롤 초기화 파일·카탈로그를
// 읽는다)가 오른다(verifyTestInputs).
tasks.test {
    inputs.file(rootProject.layout.projectDirectory.file("docs/설계서.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("docker/postgres/init-roles.sql")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")).withPathSensitivity(PathSensitivity.RELATIVE)
}

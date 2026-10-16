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
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())
                implementation(testFixtures(project(":portal-infra")))
                implementation(libs.platform.spring)
                implementation(platform(libs.spring.boot.bom))
                implementation(libs.spring.boot.starter.test)
                implementation(libs.spring.boot.starter.actuator)
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
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

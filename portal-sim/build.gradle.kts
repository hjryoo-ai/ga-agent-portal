// 설계서 §3.3 portal-sim — 엔진 시뮬레이션 프록시·simulation_log(포털 Phase 5). Spring·DB 무의존: 로그 저장·트랜잭션·토큰·정책 키·지표는
// 포트이고 portal-app이 구현한다(의존 방향 → projection → domain).
plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(project(":portal-projection"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.json.schema.validator)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(testFixtures(libs.platform.core))
    // 시험 픽스처: 엔진 샘플 스텁(StubSimulationEngine — 엔진 사본 샘플의 요청을 JCS 정규형으로 대조) — portal-app 통합 시험·E2E 하네스도 쓴다
    testFixturesApi(platform(libs.spring.boot.bom))
    testFixturesApi(libs.jackson.databind)
    testFixturesImplementation(libs.platform.canonical)
}

// 계약 사본(원격 조회 0 — SimContracts): 포털 요청 스키마(portal-api)와 엔진 결과 스키마(engine-internal 사본)와 그 정의(defs).
tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("contracts")) {
        into("contracts")
        include("api/v1/portal-api.openapi.yaml", "api/v1/engine-internal.openapi.yaml", "events/v1/defs.schema.json")
    }
}

// 시험 픽스처(StubSimulationEngine)와 시험이 엔진 샘플 사본을 읽는다 — 입력으로 선언(verifyTestInputs).
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts/shared/ga-commission-engine")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("ga.repoRoot", rootDir.absolutePath)
}

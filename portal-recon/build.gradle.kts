// 설계서 §3.3 portal-recon — 대사 판정·회차·자동 복구(포털 Phase 7). Spring·DB 무의존: 저장·트랜잭션·잠금·엔진 주소·토큰·재구축은 포트이고
// portal-app이 구현한다(의존 방향 → projection → domain — ingest를 보지 않는다, 규칙 (a)).
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
    // 시험 픽스처: 엔진 명세 샘플 스텁(StubStatementEngine) — portal-app 통합 시험·E2E 하네스도 쓴다
    testFixturesApi(platform(libs.spring.boot.bom))
    testFixturesApi(libs.jackson.databind)
}

// 계약 사본(원격 조회 0 — ReconContracts): 명세 payload 스키마 둘·그 정의(defs)·엔진 계약 사본(Problem).
tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("contracts")) {
        into("contracts")
        include("events/v1/payloads/StatementEstimated.v1.schema.json", "events/v1/payloads/StatementConfirmed.v1.schema.json",
            "events/v1/defs.schema.json", "api/v1/engine-internal.openapi.yaml")
    }
}

// 시험 픽스처(StubStatementEngine)와 시험이 엔진 샘플 사본을 읽는다 — 입력으로 선언(verifyTestInputs).
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts/shared/ga-commission-engine")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("ga.repoRoot", rootDir.absolutePath)
}

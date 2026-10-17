// 설계서 §3.3 portal-ingest — 피드 클라이언트·수신 검증·멱등 적용기·워커(Phase 1). Spring·DB 무의존: DB 저장·트랜잭션·잠금·토큰은
// 포트이고 portal-app이 구현한다(계획 Q6 — infra는 ingest를 볼 수 없다, 규칙 (a)).
plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":portal-projection"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.platform.canonical)
    implementation(libs.json.schema.validator)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(testFixtures(libs.platform.core))
    testImplementation(testFixtures(project(":portal-projection")))
    // 시험 픽스처: 피드 계약 스위트(FeedClientContract)·스텁 피드 서버(JDK HttpServer)·샘플 읽기 — portal-app 통합 시험도 쓴다
    testFixturesApi(project(":portal-projection"))
    testFixturesApi(platform(libs.spring.boot.bom))
    testFixturesApi(libs.jackson.databind)
    testFixturesApi(libs.junit.jupiter)
    testFixturesApi(libs.assertj.core)
}

// 계약 사본(원격 조회 0 — ContractSchemas): 저장소 contracts/에서 빌드가 복사한다. 원본은 하나다.
tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("contracts")) {
        into("contracts")
        include("events/v1/**", "api/v1/**", "shared/ga-disclosure/events/v1/**")
    }
}

// ClasspathContractsTest가 저장소 contracts/를 읽는다(클래스패스 사본 = 저장소 바이트) — 입력 선언(verifyTestInputs).
// 시험 스트림(src/test/resources/streams)은 샘플에서 만든 것이라 StreamFilesTest도 contracts/를 읽는다.
tasks.test {
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts")).withPathSensitivity(PathSensitivity.RELATIVE)
    // 클래스패스에 오른 portal-projection 시험 픽스처(골든 뷰·스트림 경로)가 읽을 수 있는 파일 — verifyTestInputs는 픽스처의 경로도 본다
    inputs.dir(rootProject.layout.projectDirectory.dir("portal-projection/src/test/resources/golden-streams")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("portal-projection/src/test/resources/golden-views")).withPathSensitivity(PathSensitivity.RELATIVE)
}

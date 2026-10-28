// 데모 테넌트의 엔진 대역(설계서 부록 B v1.11, 포털 Phase 8 ② — 계획 P-5): 엔진이 낸 데모 묶음(contracts/shared/ga-commission-engine/samples/demo/)으로
// 엔진 내부 계약 세 경로(피드·명세·시뮬레이션)를 섬기는 JDK HTTP 서버. 계산 0 — 곱셈·산술 스캔 대상(9모듈). Spring·DB 무의존,
// 계층 규칙상 portal-domain·platform-core만 본다(ingest의 파일 피드·하네스 스텁은 쓰지 않는다).
plugins {
    application
}

dependencies {
    implementation(project(":portal-domain"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(testFixtures(libs.platform.core))
    // 피드 계약 스위트(FeedClientContract)를 포털의 HTTP 클라이언트로 — 시험 원천만(계층 규칙은 제품 클래스를 본다)
    testImplementation(project(":portal-ingest"))
    testImplementation(testFixtures(project(":portal-ingest")))
    testImplementation(testFixtures(project(":portal-projection")))
}

application {
    mainClass = "com.ga.portal.demo.DemoMain"
}

// 시험이 엔진 데모 묶음 사본과(DemoServerTest) 피드 계약 스위트 픽스처의 이벤트 샘플을(contracts/events/v1/samples) 읽는다 — 입력으로 선언(verifyTestInputs).
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts")).withPathSensitivity(PathSensitivity.RELATIVE)
    // 클래스패스에 오른 portal-ingest·portal-projection 시험 픽스처가 읽을 수 있는 파일(portal-ingest의 시험 태스크와 같은 선언)
    inputs.dir(rootProject.layout.projectDirectory.dir("portal-ingest/src/test/resources/streams")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("portal-projection/src/test/resources/golden-streams")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("portal-projection/src/test/resources/golden-views")).withPathSensitivity(PathSensitivity.RELATIVE)
    // DemoComposeFilesTest(포털 Phase 8 ②·③): compose·렐름·등록 SQL·비밀 생성기·이미지 빌드 파일, README의 데모 비밀번호 줄
    inputs.dir(rootProject.layout.projectDirectory.dir("docker")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("README.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("ga.repoRoot", rootDir.absolutePath)
}

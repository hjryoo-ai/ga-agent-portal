// 설계서 §3.3 portal-inbox — 알림함·이의제기 API의 유스케이스(포털 Phase 6 계획 Q11). portal-query는 읽기 전용 트랜잭션이 규약이라 쓰기(읽음·문의 접수)를
// 여기에 둔다. Spring·DB 무의존: 저장·트랜잭션은 포트이고 portal-app이 구현한다(의존 방향 → projection → domain).
dependencies {
    implementation(project(":portal-projection"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.json.schema.validator)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(testFixtures(libs.platform.core))
}

// 계약 사본(원격 조회 0 — InboxContracts): 문의 요청 스키마의 정본은 portal-api.
tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("contracts")) {
        into("contracts")
        include("api/v1/portal-api.openapi.yaml")
    }
}

// 계약 시험(InboxApiContractTest)이 저장소의 계약 파일을 읽는다 — 입력으로 선언(verifyTestInputs).
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.layout.projectDirectory.file("contracts/api/v1/portal-api.openapi.yaml")).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("ga.repoRoot", rootDir.absolutePath)
}

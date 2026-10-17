// 설계서 §3.3 portal-query — 조회 유스케이스·인가 표·응답 규칙(포털 Phase 3). Spring·DB 무의존: 저장소·키·트랜잭션은 포트로 받는다(어댑터는 portal-app).
dependencies {
    implementation(project(":portal-projection"))
}

dependencies {
    // PortalApiContractTest: 계약 YAML(contracts/api/v1/portal-api.openapi.yaml)과 응답 레코드를 양방향 대조
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.jackson.dataformat.yaml)
}

tasks.test {
    inputs.file(rootProject.layout.projectDirectory.file("contracts/api/v1/portal-api.openapi.yaml")).withPathSensitivity(PathSensitivity.RELATIVE)
}

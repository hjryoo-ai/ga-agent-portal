// 설계서 §3.3 portal-recon — 대사 판정·회차·자동 복구(포털 Phase 7). Spring·DB 무의존: 저장·트랜잭션·잠금·엔진 HTTP·재구축은 포트이고
// portal-app이 구현한다(의존 방향 → projection → domain — ingest를 보지 않는다, 규칙 (a)).
dependencies {
    implementation(project(":portal-projection"))

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(testFixtures(libs.platform.core))
}

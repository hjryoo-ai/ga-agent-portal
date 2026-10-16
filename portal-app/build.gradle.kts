// Spring Boot 조립(설계서 §3.3). Phase 0: 진입점 + /actuator/health(DB 헬스 비활성), archTest(전 모듈 아키텍처 규칙·SQL 스캔).
dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":portal-ingest"))
    implementation(project(":portal-query"))
    implementation(project(":portal-sim"))
    implementation(project(":portal-recon"))
    implementation(project(":portal-infra"))
}

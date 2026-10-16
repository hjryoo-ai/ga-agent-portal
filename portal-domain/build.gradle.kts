// 포털 고유 값객체·상태 열거형(설계서 §3.3). Won·Ratio·Ym·TenantId·AgentId·TenantContext는 platform-core 0.2.0의 것을 쓴다 — 같은 이름의 클래스를 만들지 않는다.
// Spring·DB 무의존(루트 verifySpringFree + ArchUnit). platform-core만 의존한다.
dependencies {
    api(libs.platform.core)

    testImplementation(testFixtures(libs.platform.core))
}

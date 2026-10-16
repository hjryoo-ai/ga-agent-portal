// PostgreSQL 어댑터(platform-spring의 TenantScopedRepository 하위 저장소), Flyway 마이그레이션·RLS(설계서 §3.3·§5).
// infra는 app에서만 조립된다(의존 방향 — ArchUnit).
dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":portal-domain"))
    implementation(libs.platform.spring)
}

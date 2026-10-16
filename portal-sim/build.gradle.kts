// 설계서 §3.3 portal-sim — Phase 0에는 코드가 없다(의존 방향만: → projection → domain).
dependencies {
    implementation(project(":portal-projection"))
}

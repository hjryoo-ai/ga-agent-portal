// 프로젝터(이벤트 → 뷰) 순수 로직 + 알림 파생 규칙(Phase 2·6). Spring·DB 무의존. Phase 0은 계약 스키마 시험만(지시문 v1.2 §5).
dependencies {
    api(project(":portal-domain"))

    // EventSchemaTest: contracts/의 JSON Schema 2020-12·OpenAPI를 그대로 읽어 샘플을 검증한다
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.dataformat.yaml)
}

// 계약 디렉터리는 소스셋 밖이다 — 테스트 입력으로 선언한다(계약만 바뀌어도 시험이 다시 돈다, verifyTestInputs·C7).
tasks.test {
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts")).withPathSensitivity(PathSensitivity.RELATIVE)
}

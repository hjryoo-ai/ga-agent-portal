// 프로젝터(이벤트 → 뷰) 순수 로직 + 알림 파생 규칙(Phase 2·6). Spring·DB 무의존. Phase 0은 계약 스키마 시험만(지시문 v1.2 §5).
plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":portal-domain"))
    // Envelope의 payload는 JSON 트리다(모르는 필드 보존 — 설계서 §4.5). Jackson은 Spring이 아니다(verifySpringFree).
    api(platform(libs.spring.boot.bom))
    api(libs.jackson.databind)

    // EventSchemaTest: contracts/의 JSON Schema 2020-12·OpenAPI를 그대로 읽어 샘플을 검증한다
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.dataformat.yaml)
    testImplementation(libs.snakeyaml)   // YAML 1.1 — ContractYamlParsersTest(계약 v1.4 N1)

    // 시험 픽스처(Phase 2): 인메모리 뷰 저장소(읽은 행 수 계수)·골든 뷰 비교·Envelope 읽기 — portal-app 통합 시험(ProjectionGoldenIT)도 쓴다
    testFixturesApi(project(":portal-projection"))
    testFixturesApi(platform(libs.spring.boot.bom))
    testFixturesApi(libs.jackson.databind)
    testFixturesApi(libs.junit.jupiter)
    testFixturesApi(libs.assertj.core)
}

// 계약 디렉터리는 소스셋 밖이다 — 테스트 입력으로 선언한다(계약만 바뀌어도 시험이 다시 돈다, verifyTestInputs·C7).
tasks.test {
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts")).withPathSensitivity(PathSensitivity.RELATIVE)
    // 골든 뷰 다시 쓰기(-Pgolden.update=true) — 쓴 뒤에는 사람이 손계산과 대조한다(보고서). 기본은 대조만.
    systemProperty("golden.update", providers.gradleProperty("golden.update").getOrElse("false"))
}

// 포털 Phase 4 화면(계획 ③): React + Vite 빌드를 Gradle로 감싼다 — ./gradlew build 한 번이 pnpm 설치·생성 클라이언트 대조·타입·린트·시험·라이선스·빌드를 돈다.
// 산출물(Vite dist)은 이 모듈 jar의 classpath:/portal-web/ 아래로 들어가고 portal-app이 같은 출처로 서빙한다(단일 아티팩트 — 지시문 ⑧).
// Node·pnpm은 플러그인이 내려받아 고정한다(버전 카탈로그). 모든 태스크는 원천·계약·락·설정 파일을 입력으로 선언한다(계약만 바뀌어도 다시 돈다).
import com.github.gradle.node.pnpm.task.PnpmInstallTask
import com.github.gradle.node.pnpm.task.PnpmTask

plugins {
    alias(libs.plugins.node.gradle)
}

node {
    version = libs.versions.nodejs.get()
    pnpmVersion = libs.versions.pnpm.get()
    download = true
    distBaseUrl = null // 저장소는 settings.gradle.kts(FAIL_ON_PROJECT_REPOS — Node.js ivy 저장소)
    npmInstallCommand = "install"
}

// 락 파일과 다르면 실패한다(지시문 ① --frozen-lockfile). 락은 의존성을 바꾼 커밋에서 손으로 갱신한다.
tasks.named<PnpmInstallTask>("pnpmInstall") {
    args.set(listOf("--frozen-lockfile"))
}

val contract = rootProject.layout.projectDirectory.file("contracts/api/v1/portal-api.openapi.yaml")
val webSources = fileTree("src") { exclude("__lint__/**") }
val webConfig = files("package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", "tsconfig.json", "vite.config.ts", "eslint.config.js", "contracts-client.lock.json")

fun PnpmTask.webInputs() {
    dependsOn(tasks.named("pnpmInstall"))
    inputs.files(webConfig).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(webSources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("scripts").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("eslint").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(contract).withPathSensitivity(PathSensitivity.RELATIVE)
}

fun PnpmTask.okMarker(name: String) {
    val marker = layout.buildDirectory.file("web/$name.ok")
    outputs.file(marker)
    doLast { marker.get().asFile.writeText("ok\n") }
}

// 계약 → src/gen/portal-api.ts(커밋본)와 잠금 파일 대조(C1). 계약이 1바이트 바뀌면 여기서 실패한다.
val clientCheck = tasks.register<PnpmTask>("clientCheck") {
    group = "verification"
    description = "Regenerates the API client from the contract and checks it against the committed file and contracts-client.lock.json."
    webInputs()
    args.set(listOf("run", "client:check"))
    okMarker("client-check")
}

// 계약을 바꾼 커밋에서만 손으로 돌린다(생성물·잠금 갱신이 그 커밋에 드러난다).
tasks.register<PnpmTask>("clientLock") {
    description = "Regenerates src/gen/portal-api.ts and rewrites contracts-client.lock.json."
    webInputs()
    args.set(listOf("run", "client:lock"))
    outputs.upToDateWhen { false }
}

val webTypecheck = tasks.register<PnpmTask>("webTypecheck") {
    group = "verification"
    description = "tsc --noEmit (strict, opaque Won)."
    webInputs()
    dependsOn(clientCheck)
    args.set(listOf("run", "typecheck"))
    okMarker("typecheck")
}

val webLint = tasks.register<PnpmTask>("webLint") {
    group = "verification"
    description = "ESLint: no money arithmetic, no hand-written network calls, no browser storage, Korean text only in messages."
    webInputs()
    dependsOn(clientCheck)
    args.set(listOf("run", "lint"))
    okMarker("lint")
}

val webTest = tasks.register<PnpmTask>("webTest") {
    group = "verification"
    description = "Vitest unit, component and lint-rule tests (JUnit XML in build/reports/vitest)."
    webInputs()
    dependsOn(clientCheck)
    args.set(listOf("run", "test"))
    outputs.dir(layout.buildDirectory.dir("reports/vitest"))
}

val webBuild = tasks.register<PnpmTask>("webBuild") {
    group = "build"
    description = "Vite build (build/web/dist), then the output scan and the bundle size cap."
    webInputs()
    dependsOn(clientCheck)
    args.set(listOf("run", "build"))
    outputs.dir(layout.buildDirectory.dir("web/dist"))
    outputs.file(layout.buildDirectory.file("web/bundle-modules.json"))
    outputs.file(layout.buildDirectory.file("web/bundle-size.json"))
}

val webLicenses = tasks.register<PnpmTask>("webLicenses") {
    group = "verification"
    description = "Checks installed packages against THIRD-PARTY.md and the license allow list (bundled packages without exceptions)."
    webInputs()
    dependsOn(webBuild)
    inputs.file("THIRD-PARTY.md").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.buildDirectory.file("web/bundle-modules.json"))
    args.set(listOf("run", "licenses"))
    okMarker("licenses")
}

// 지시문 ①·C12: pnpm audit 기록(결과로 실패하지 않는다, 올림 없음). 레지스트리에 묻기 때문에 매번 돈다 — check에는 넣지 않고 CI가 따로 부른다.
tasks.register<PnpmTask>("webAudit") {
    group = "verification"
    description = "Records pnpm audit --json in build/web/audit.json (never fails on findings)."
    webInputs()
    args.set(listOf("run", "audit:record"))
    outputs.upToDateWhen { false }
}

tasks.named<ProcessResources>("processResources") {
    from(webBuild.map { it.outputs.files.files.first { f -> f.name == "dist" } }) {
        into("portal-web")
        exclude(".vite/**")
    }
}

tasks.named("check") {
    dependsOn(clientCheck, webTypecheck, webLint, webTest, webLicenses)
}

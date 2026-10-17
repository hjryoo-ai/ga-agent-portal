rootProject.name = "ga-agent-portal"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // JDK 25 툴체인 자동 프로비저닝(로컬에 JDK 25가 없어도 빌드 가능)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// ---------------------------------------------------------------------------------------------
// 플랫폼 아티팩트(com.ga.platform, 발행 저장소 ga-disclosure) — 환경마다 정확히 한 저장소에서만 찾는다(지시문 v1.2 §1·C9).
// - CI(CI=true): GitHub Packages만. Maven 레지스트리는 저장소 스코프라 이 저장소의 GITHUB_TOKEN으로는 읽지 못한다 —
//   PAT(classic, read:packages)를 저장소 비밀 GHP_USER·GHP_READ_TOKEN으로 받는다(계획 승인 §3). 없으면 여기서 바로 실패한다.
// - 로컬: mavenLocal()만(PAT 불요). CI에서는 mavenLocal을 쓰지 않는다.
// 어느 쪽이든 받은 파일의 SHA-256은 gradle/verification-metadata.xml과 같아야 한다 — 바이트가 다른 사본은 어디서 오든 실패.
// ---------------------------------------------------------------------------------------------
val onCi = providers.environmentVariable("CI").orNull == "true"
val platformRepoUrl = "https://maven.pkg.github.com/hjryoo-ai/ga-disclosure"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        exclusiveContent {
            forRepository {
                if (onCi) {
                    val user = providers.environmentVariable("GHP_USER").orNull
                    val token = providers.environmentVariable("GHP_READ_TOKEN").orNull
                    if (user.isNullOrBlank() || token.isNullOrBlank()) {
                        throw GradleException(
                            "CI에서 플랫폼 아티팩트를 읽을 자격 증명이 없다: 환경변수 GHP_USER·GHP_READ_TOKEN(저장소 비밀)이 비어 있다. " +
                                "PAT(classic)의 범위 read:packages와 비밀 이름 철자를 확인하라. 포크 PR에는 비밀이 전달되지 않는다.",
                        )
                    }
                    maven {
                        name = "GitHubPackages"
                        url = uri(platformRepoUrl)
                        credentials {
                            username = user
                            password = token
                        }
                    }
                } else {
                    mavenLocal()
                }
            }
            filter { includeGroup("com.ga.platform") }
        }
        mavenCentral()
        // web(포털 Phase 4): node-gradle 플러그인이 내려받는 Node.js 배포본. 프로젝트 저장소 금지 규약 때문에 여기 두고, 그 모듈 하나로 좁힌다.
        // 받은 파일의 SHA-256은 gradle/verification-metadata.xml에 고정된다.
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
    }
}

include(
    "portal-domain",
    "portal-projection",
    "portal-ingest",
    "portal-query",
    "portal-sim",
    "portal-recon",
    "portal-infra",
    "portal-app",
    "portal-demo",
    "web",
)

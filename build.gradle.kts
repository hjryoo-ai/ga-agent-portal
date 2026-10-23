import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.security.MessageDigest

plugins {
    base
    alias(libs.plugins.spring.boot) apply false
}

val catalog = libs

fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

// ---------------------------------------------------------------------------------------------
// 공통: 의존성 락 + net.jqwik 차단 (CLAUDE.md 절대 규칙 7)
// ---------------------------------------------------------------------------------------------
allprojects {
    group = "com.ga.portal"
    version = "0.1.0"

    dependencyLocking {
        lockAllConfigurations()
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "net.jqwik") {
                throw GradleException(
                    "CLAUDE.md 절대 규칙 7 위반: '${requested.group}:${requested.name}' 의존은 금지다 " +
                        "(설정 '${this@configureEach.name}'). jqwik은 1.10부터 AI 코딩 에이전트 사용 배제 조항과 " +
                        "테스트 출력 삽입 지시문을 가지며 JUnit Platform 1.14를 요구해 Boot 4의 JUnit 6과 비호환이다. " +
                        "속성 테스트는 platform-core 테스트 픽스처의 SeededCases를 쓴다."
                )
            }
        }
    }

    // ./gradlew resolveAndLockAll --write-locks [--write-verification-metadata sha256] 로 모든 해석 가능한 설정의 락(과 검증 메타데이터)을 갱신한다.
    tasks.register("resolveAndLockAll") {
        group = "dependency locking"
        description = "Resolves every resolvable configuration so that --write-locks records it."
        notCompatibleWithConfigurationCache("resolves configurations at execution time")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) { "--write-locks 와 함께 실행한다" }
        }
        doLast {
            configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
        }
    }

    // CI가 전 모듈 의존성 트리에 net.jqwik이 없음을 확인할 때 쓴다.
    tasks.register<DependencyReportTask>("allDependencies")
}

// ---------------------------------------------------------------------------------------------
// Java 모듈 공통 규약
// ---------------------------------------------------------------------------------------------
subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(catalog.versions.java.get())
        }
    }

    // -Xpkginfo:always: 아직 코드가 없는 패키지(Phase 0의 ingest·query·sim·recon·demo)도 package-info.class가 생겨,
    // 아키텍처 규칙 허용 목록의 폐기 항목 검사가 그 패키지의 존재를 확인할 수 있다(ArchRules.stalePackages).
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror", "-parameters", "-Xpkginfo:always"))
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("ga.repoRoot", rootDir.absolutePath)
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    dependencies {
        "testImplementation"(platform(catalog.spring.boot.bom))
        "testImplementation"(catalog.junit.jupiter)
        "testImplementation"(catalog.assertj.core)
        "testRuntimeOnly"(catalog.junit.platform.launcher)
    }
}

// ---------------------------------------------------------------------------------------------
// Spring·DB 무의존 모듈(지시문 §1): 컴파일·런타임 클래스패스에 org.springframework 그룹이 없어야 한다.
// (java.sql / javax.sql 은 JDK 모듈이라 클래스패스로 막을 수 없으므로 ArchUnit이 막는다.)
// ---------------------------------------------------------------------------------------------
val springFreeModules = listOf("portal-domain", "portal-projection", "portal-ingest", "portal-query", "portal-sim")   // portal-query: Phase 3 계획 F7, portal-sim: Phase 5

// Boot BOM(spring-boot-dependencies)은 버전 제약만 담은 POM이라 클래스가 없다 — 검사에서 제외한다.
val constraintOnlyPlatforms = setOf("org.springframework.boot:spring-boot-dependencies")

fun collectGroups(root: ResolvedComponentResult): Set<String> {
    val seen = mutableSetOf<ResolvedComponentResult>()
    val queue = ArrayDeque(listOf(root))
    val groups = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
        val c = queue.removeFirst()
        if (!seen.add(c)) continue
        val mv = c.moduleVersion
        if (mv != null && "${mv.group}:${mv.name}" !in constraintOnlyPlatforms) groups += mv.group
        c.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue += it.selected }
    }
    return groups
}

springFreeModules.forEach { path ->
    project(":$path") {
        val verify = tasks.register("verifySpringFree") {
            group = "verification"
            description = "Fails when org.springframework artifacts appear on main classpaths."
            val roots = listOf("compileClasspath", "runtimeClasspath").map { name ->
                configurations.named(name).flatMap { it.incoming.resolutionResult.rootComponent }
            }
            doLast {
                val offending = roots.flatMap { collectGroups(it.get()) }
                    .filter { it.startsWith("org.springframework") }
                    .toSortedSet()
                if (offending.isNotEmpty()) {
                    throw GradleException("$path 은 Spring 무의존 모듈이다. 발견된 그룹: $offending")
                }
            }
        }
        tasks.named("check") { dependsOn(verify) }
    }
}

// ---------------------------------------------------------------------------------------------
// C9: SNAPSHOT 0 — 버전 카탈로그와 모든 락 파일(해석된 버전 전부)에 SNAPSHOT이 없어야 한다. 해석하지 않고 파일만 읽는다.
// ---------------------------------------------------------------------------------------------
val verifyNoSnapshots = tasks.register("verifyNoSnapshots") {
    group = "verification"
    description = "Fails when the version catalog or any dependency lock file mentions a SNAPSHOT version."
    val files = listOf(file("gradle/libs.versions.toml"), file("settings-gradle.lockfile")) +
        (listOf(rootDir) + subprojects.map { it.projectDir }).map { it.resolve("gradle.lockfile") }.filter { it.isFile }
    files.forEach { inputs.file(it) }
    doLast {
        val hits = files.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> !l.trimStart().startsWith("#") && l.contains("SNAPSHOT", ignoreCase = true) }
                .map { (i, l) -> "${f.relativeTo(rootDir)}:${i + 1}: ${l.trim()}" }
        }
        if (hits.isNotEmpty()) {
            throw GradleException("SNAPSHOT 의존은 금지다(지시문 v1.2 §1·C9):\n" + hits.joinToString("\n"))
        }
        logger.lifecycle("verifyNoSnapshots: ${files.size} files, 0 SNAPSHOT")
    }
}
tasks.named("check") { dependsOn(verifyNoSnapshots) }

// ---------------------------------------------------------------------------------------------
// C9: 플랫폼 0.2.0 고정 — ga-disclosure 인계 §6 표(발행 run 38552151950의 sha256sum, 13개 파일)가 1차 출처다.
// verifyPlatformPins(check): gradle/verification-metadata.xml의 com.ga.platform 항목이 그 표와 정확히 같다(눈이 아니라 시험 — 계획 승인 Q8).
// printPlatformArtifacts(CI): 실제로 해석된 13개 파일의 경로·SHA-256을 출력하고 표와 대조한다 — 경로가 Gradle 캐시(원격에서 받음)인지 ~/.m2인지가
// "발행본에서 해석"의 1차 증거다(계획 승인 B2). 자격 증명 값은 출력하지 않는다.
// ---------------------------------------------------------------------------------------------
val handoffCopy = layout.projectDirectory.file("docs/handoff/ga-disclosure-portal-phase0.md")
val verificationMetadata = layout.projectDirectory.file("gradle/verification-metadata.xml")
val platformVersion = catalog.versions.ga.platform.get()

fun handoffPlatformTable(handoff: File): Map<String, String> {
    val row = Regex("""^\s*\|\s*`(platform-[a-z]+-[0-9.]+(?:-[a-z-]+)?\.(?:jar|pom|module))`\s*\|\s*`([0-9a-f]{64})`\s*\|\s*$""")
    return handoff.readLines().mapNotNull { row.find(it) }.associate { it.groupValues[1] to it.groupValues[2] }
}

fun metadataPlatformEntries(metadata: File): Map<String, String> {
    val component = Regex("""<component group="com\.ga\.platform" name="([^"]+)" version="([^"]+)">(.*?)</component>""", RegexOption.DOT_MATCHES_ALL)
    val artifact = Regex("""<artifact name="([^"]+)">\s*<sha256 value="([0-9a-f]{64})"""")
    return component.findAll(metadata.readText()).flatMap { c -> artifact.findAll(c.groupValues[3]).map { it.groupValues[1] to it.groupValues[2] } }
        .toMap()
}

val verifyPlatformPins = tasks.register("verifyPlatformPins") {
    group = "verification"
    description = "Fails unless the com.ga.platform entries of gradle/verification-metadata.xml equal the handoff §6 table."
    inputs.file(handoffCopy)
    inputs.file(verificationMetadata)
    val handoff = handoffCopy.asFile
    val metadata = verificationMetadata.asFile
    val version = platformVersion
    doLast {
        val table = handoffPlatformTable(handoff).filterKeys { it.contains("-$version") }
        if (table.size != 13) throw GradleException("인계 §6 표에서 플랫폼 $version 파일 13개를 읽지 못했다: ${table.keys}")
        val pinned = metadataPlatformEntries(metadata)
        if (pinned != table) {
            val diff = (table.keys + pinned.keys).toSortedSet().filter { table[it] != pinned[it] }
                .map { "$it: 인계=${table[it] ?: "없음"} 메타데이터=${pinned[it] ?: "없음"}" }
            throw GradleException("검증 메타데이터의 플랫폼 SHA-256이 인계 §6 표와 다르다 — 멈추고 보고한다:\n" + diff.joinToString("\n"))
        }
        logger.lifecycle("verifyPlatformPins: ${pinned.size} com.ga.platform:$version files pinned, equal to the handoff §6 table")
    }
}
tasks.named("check") { dependsOn(verifyPlatformPins) }

val platformProbe = configurations.create("platformProbe") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}
dependencies {
    platformProbe(catalog.platform.core)
    platformProbe(catalog.platform.canonical)
    platformProbe(catalog.platform.spring)
    platformProbe(testFixtures(catalog.platform.core))
}

tasks.register("printPlatformArtifacts") {
    group = "verification"
    description = "Resolves the platform artifacts (jars, sources, POM, module metadata), prints path and SHA-256, and checks them against the handoff table."
    notCompatibleWithConfigurationCache("uses an artifact resolution query")
    val handoff = handoffCopy.asFile
    val version = platformVersion
    doLast {
        val jars = platformProbe.resolve()
        // 소스 jar와 POM도 받는다 — 검증 메타데이터가 그 해시도 고정한다. Gradle은 .module이 있으면 POM을 읽지 않으므로(받지도 검증하지도 않는다)
        // 여기서 명시적으로 받아 13개 전부가 검증을 거치게 한다. .module은 jar를 해석할 때 받은 것이 같은 버전 디렉터리에 있다.
        val ids = platformProbe.incoming.resolutionResult.allComponents.mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
        val extra = dependencies.createArtifactResolutionQuery().forComponents(ids)
            .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java).execute().resolvedComponents
            .flatMap { it.getArtifacts(SourcesArtifact::class.java) } +
            dependencies.createArtifactResolutionQuery().forComponents(ids)
                .withArtifacts(org.gradle.maven.MavenModule::class.java, org.gradle.maven.MavenPomArtifact::class.java).execute().resolvedComponents
                .flatMap { it.getArtifacts(org.gradle.maven.MavenPomArtifact::class.java) }
        val unresolved = extra.filterIsInstance<org.gradle.api.artifacts.result.UnresolvedArtifactResult>()
        if (extra.size != 6 || unresolved.isNotEmpty()) {
            throw GradleException("플랫폼 소스·POM 6개를 받지 못했다: ${unresolved.map { "${it.id}: ${it.failure.message}" }}")
        }
        val versionDirs = jars.map { jar -> generateSequence(jar.parentFile) { it.parentFile }.first { it.name == version } }.toSet()
        val files = versionDirs.flatMap { d -> d.walkTopDown().filter { it.isFile && it.name.startsWith("platform-") && it.name.contains("-$version") }.toList() }
            .filter { it.extension in setOf("jar", "pom", "module") }
            .distinctBy { it.name }
            .sortedBy { it.name }
        val table = handoffPlatformTable(handoff)
        files.forEach { println("PLATFORM_SHA256 ${sha256(it)}  ${it.name}  ${it.absolutePath}") }
        val wrong = files.filter { table[it.name] != sha256(it) }.map { it.name }
        if (files.size != 13 || wrong.isNotEmpty()) {
            throw GradleException("플랫폼 파일 ${files.size}개(기대 13), 인계 §6 표와 다른 것: $wrong")
        }
        val fromMavenLocal = files.any { it.absolutePath.contains("/.m2/repository/") }
        logger.lifecycle("printPlatformArtifacts: 13 files equal to the handoff §6 table, source=" + if (fromMavenLocal) "mavenLocal" else "remote (Gradle cache)")
    }
}

// ---------------------------------------------------------------------------------------------
// 계약(지시문 v1.2 §5, H4·H5 — 포털 Phase 5 P-22에서 업스트림 둘로): contracts/는 두 부분이다.
//  - contracts/shared/<업스트림>/ : 다른 저장소가 소유한 계약의 사본. 고치지 않는다. 닫힌 목록 SHARED_UPSTREAMS.
//      ga-disclosure          : 공유 Envelope 핵심(events/v1/envelope-core.schema.json).
//      ga-commission-engine   : 시뮬레이션 요청·응답 샘플(samples/simulations/ — 포털 Phase 5 Q17, 스텁·E2E의 유일한 엔진 데이터).
//    디렉터리마다 CHECKSUMS(평문 SHA-256, sha256sum 형식)와 UPSTREAM — 첫 줄 owner/repo@<40자 병합 커밋>[:<계약 디렉터리>](생략하면 contracts —
//    엔진은 git 루트 밑 commission-system/contracts), 다음 줄부터 그 계약 디렉터리 기준 파일 목록.
//  - 그 밖(contracts/events/v1, contracts/api/v1): 포털 정본. contracts/CHECKSUMS(같은 형식, shared/ 제외)는 엔진이 사본을 들일 때 대조한다.
//  - contractChecksums       : CHECKSUMS 셋을 쓴다.
//  - verifyContractChecksums : CHECKSUMS가 파일과 다르거나 shared/ 디렉터리 집합이 닫힌 목록과 다르면 실패(오프라인, check에 연결).
//  - verifyUpstreamContract  : shared/ 사본만 UPSTREAM의 커밋과 바이트 대조(네트워크, CI 전용). 엔진처럼 contracts/ 전체를 대조하면 포털 정본이
//                              업스트림에 없어 실패한다(H4). 사본 파일 집합 = UPSTREAM 목록(닫힌 목록 — 남거나 빠지면 실패).
// ---------------------------------------------------------------------------------------------
val contractsDir = layout.projectDirectory.dir("contracts")
val SHARED_UPSTREAMS = listOf("ga-commission-engine", "ga-disclosure")

fun contractFiles(dir: File, exclude: (File) -> Boolean = { false }): List<File> = dir.walkTopDown()
    .onEnter { !exclude(it) }
    .filter { it.isFile && it.name != "CHECKSUMS" && it.name != "UPSTREAM" && it.name != ".DS_Store" }
    .toList()

fun checksumText(dir: File, files: List<File>): String = files
    .map { it.relativeTo(dir).invariantSeparatorsPath to sha256(it) }
    .sortedBy { it.first }
    .joinToString("") { "${it.second}  ${it.first}\n" }

fun ownContractFiles(dir: File) = contractFiles(dir) { it == dir.resolve("shared") }

fun sharedDirs(dir: File): List<File> {
    val present = dir.resolve("shared").listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()
    if (present != SHARED_UPSTREAMS) {
        throw GradleException("contracts/shared/ 업스트림 디렉터리는 $SHARED_UPSTREAMS 이다: $present")
    }
    return SHARED_UPSTREAMS.map { dir.resolve("shared/$it") }
}

tasks.register("contractChecksums") {
    group = "contracts"
    description = "Writes contracts/CHECKSUMS (portal-owned files) and contracts/shared/<upstream>/CHECKSUMS (sha256sum format, sorted by path)."
    val dir = contractsDir.asFile
    doLast {
        dir.resolve("CHECKSUMS").writeText(checksumText(dir, ownContractFiles(dir)))
        sharedDirs(dir).forEach { it.resolve("CHECKSUMS").writeText(checksumText(it, contractFiles(it))) }
    }
}

val verifyContractChecksums = tasks.register("verifyContractChecksums") {
    group = "verification"
    description = "Fails when contracts/CHECKSUMS or a contracts/shared/<upstream>/CHECKSUMS is stale."
    val dir = contractsDir.asFile
    inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE)
    doLast {
        (listOf(dir to ownContractFiles(dir)) + sharedDirs(dir).map { it to contractFiles(it) }).forEach { (d, files) ->
            val actual = d.resolve("CHECKSUMS").takeIf { it.exists() }?.readText() ?: ""
            if (checksumText(d, files) != actual) {
                throw GradleException("${d.relativeTo(rootDir).invariantSeparatorsPath}/CHECKSUMS 가 계약 파일과 다르다. " +
                    "사본(shared/)은 고치지 않는다 — 업스트림에서 다시 받고 ./gradlew contractChecksums 로 갱신하라.")
            }
        }
        logger.lifecycle("verifyContractChecksums: contracts/CHECKSUMS and contracts/shared/{${SHARED_UPSTREAMS.joinToString(",")}}/CHECKSUMS match")
    }
}
tasks.named("check") { dependsOn(verifyContractChecksums) }

tasks.register("verifyUpstreamContract") {
    group = "verification"
    description = "Compares each file under contracts/shared/<upstream> with that upstream at the commit pinned in its UPSTREAM (network, CI)."
    val dir = contractsDir.asFile
    doLast {
        sharedDirs(dir).forEach { shared ->
            val name = shared.name
            val lines = shared.resolve("UPSTREAM").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            val match = Regex("^([\\w.-]+/[\\w.-]+)@([0-9a-f]{40})(?::([\\w.-]+(?:/[\\w.-]+)*))?$").matchEntire(lines.firstOrNull() ?: "")
                ?: throw GradleException("contracts/shared/$name/UPSTREAM 첫 줄은 owner/repo@<40자 커밋>[:<계약 디렉터리>]이어야 한다: ${lines.firstOrNull()}")
            val (repo, commit, prefixGroup) = match.destructured
            val prefix = prefixGroup.ifEmpty { "contracts" }
            val listed = lines.drop(1).toSortedSet()
            val present = contractFiles(shared).map { it.relativeTo(shared).invariantSeparatorsPath }.toSortedSet()
            if (listed != present || listed.isEmpty()) {
                throw GradleException("contracts/shared/$name 사본 파일 집합이 UPSTREAM 목록과 다르다: 목록=$listed, 사본=$present")
            }
            listed.forEach { path ->
                val upstream = java.net.URI("https://raw.githubusercontent.com/$repo/$commit/$prefix/$path").toURL().openStream().use { it.readBytes() }
                val local = shared.resolve(path).readBytes()
                val up = MessageDigest.getInstance("SHA-256").digest(upstream).joinToString("") { "%02x".format(it) }
                val copy = sha256(shared.resolve(path))
                if (up != copy || !upstream.contentEquals(local)) {
                    throw GradleException("contracts/shared/$name/$path differs from $repo@$commit:$prefix/$path " +
                        "(copy sha256=$copy, upstream sha256=$up) — 사본을 고치지 말고 업스트림에서 다시 받는다")
                }
                logger.lifecycle("contracts/shared/$name/$path == $repo@$commit:$prefix/$path ($up)")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 평문 스캔 골격(지시문 v1.2 §7): 모든 모듈의 시험 결과 XML(표준 출력·오류·실패 메시지·시험 이름)과 빌드 디렉터리의 *.log에 센티널
// (원문·UTF-8 16진)이 한 번도 나타나지 않아야 한다. 센티널은 portal-infra 시험 픽스처의 pii-sentinels.properties(허구 값). 모든 Test 태스크 뒤에
// 돌고 check에 묶인다. 포털 Phase 3부터 설계사 식별 정보가 들어오면 센티널을 늘린다(ga-disclosure Phase 2 P4와 같은 방식).
// ---------------------------------------------------------------------------------------------
val sentinelsFile = layout.projectDirectory.file("portal-infra/src/testFixtures/resources/pii-sentinels.properties")

val scanPlaintextLeaks = tasks.register("scanPlaintextLeaks") {
    group = "verification"
    description = "Fails when a plaintext sentinel appears in any test result XML, build log or web output."
    val sentinels = sentinelsFile.asFile
    val root = layout.projectDirectory.asFile
    inputs.file(sentinels)
    outputs.upToDateWhen { false }
    doLast {
        val props = java.util.Properties()
        sentinels.reader(Charsets.UTF_8).use { props.load(it) }
        // names: 허구 개인정보(설계사 이름), secrets: 허구 서비스 토큰(포털 Phase 1 — J10), identifiers: 허구 subject·설계사 ID·증권번호
        // (포털 Phase 3 — D-14). 셋 다 원문·UTF-8 16진으로 찾는다.
        // e2e: 화면 E2E 손 스트림의 증권번호·이웃 ID(포털 Phase 4)
        val plain = listOf("names", "secrets", "identifiers", "e2e").flatMap { key -> (props.getProperty(key) ?: "").split(",") }
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (plain.isEmpty()) throw GradleException("scanPlaintextLeaks: no sentinels in ${sentinels.relativeTo(root)}")
        val forbidden = plain + plain.map { v -> v.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) } }
        val testFiles = root.listFiles().orEmpty()
            .map { it.resolve("build") }
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown().filter { f ->
                    f.isFile && ((f.name.endsWith(".xml") && f.path.contains("${File.separator}test-results${File.separator}")) || f.name.endsWith(".log"))
                }.toList()
            }
        // 화면(포털 Phase 4 C12): 배포 산출물 전부, Vitest 결과, E2E 산출물(있으면)
        val web = root.resolve("web/build")
        val webFiles = listOf("web/dist", "reports/vitest", "e2e").map { web.resolve(it) }.filter { it.isDirectory }
            .flatMap { d -> d.walkTopDown().filter { it.isFile }.toList() }
        // 시험 하나만 돈 실행(finalizedBy)에는 화면 산출물이 없을 수 있다 — check는 :web:webBuild를 먼저 돌리므로 거기서는 늘 있다
        if (webFiles.isEmpty()) logger.lifecycle("scanPlaintextLeaks: no web output yet (web/build/web/dist) — scanning test results only")
        val files = (testFiles + webFiles).distinct()
        if (files.none { it.name.endsWith(".xml") }) {
            throw GradleException("scanPlaintextLeaks: no test result XML found — run the tests first")
        }
        val hits = files.flatMap { f ->
            val text = f.readText(Charsets.UTF_8)
            forbidden.filter { text.contains(it) }.map { "${f.relativeTo(root)}: ${it.take(1)}…" }
        }
        if (hits.isNotEmpty()) {
            throw GradleException("평문 센티널이 시험 출력에 나타났다(값은 앞 1자만 표시):\n" + hits.joinToString("\n"))
        }
        logger.lifecycle("scanPlaintextLeaks: ${files.size} files, ${forbidden.size} forbidden strings, 0 hits")
    }
}
subprojects {
    tasks.withType<Test>().configureEach { finalizedBy(scanPlaintextLeaks) }
}
scanPlaintextLeaks.configure {
    mustRunAfter(subprojects.map { p -> p.tasks.withType<Test>() })
    // 화면 산출물(포털 Phase 4)을 만든 뒤에 본다 — check는 webBuild를 거치므로(webLicenses) 순서만 정한다
    mustRunAfter(":web:webBuild", ":web:webTest")
}
tasks.named("check") { dependsOn(scanPlaintextLeaks, ":web:webBuild") }

// ---------------------------------------------------------------------------------------------
// CLAUDE.md 코드 규약 — "소스셋 밖의 파일을 읽는 테스트는 그 파일을 Gradle 입력으로 선언한다"(ga-disclosure Phase 8 BUG-3, 지시문 v1.2 §7):
// 테스트 태스크마다 그 원천(+ 클래스패스에 오른 testFixtures)의 문자열 리터럴에서 저장소 경로(첫 마디가 저장소 최상위 이름)를 모아,
// 선언하지 않은 것이 있으면 실패한다. 덮였다고 보는 것: ① 매번 도는 태스크(outputs.upToDateWhen { false }) ② 선언 입력 — 슬래시가 있는 경로는
// 그 파일(디렉터리면 그 아래 파일 전부), 최상위 이름 하나(예: "docs")는 그 아래 입력 하나 이상 ③ 클래스패스에 오른 모듈의 src/main 아래와 태스크 자기 소스셋.
// (ga-disclosure 루트 build.gradle.kts의 같은 태스크를 그대로 옮겼다.)
// ---------------------------------------------------------------------------------------------
val verifyTestInputs = tasks.register("verifyTestInputs") {
    group = "verification"
    description = "Fails when a test reads a repository file outside its source sets without declaring it as a task input."
    val root = rootDir
    doLast {
        val tops = root.listFiles().orEmpty().map { it.name }.filter { (!it.startsWith(".") || it == ".github") && it != "build" }.toSet()
        val literal = Regex("\"([^\"\\\\\\n]+)\"")
        val blockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val lineComments = Regex("(?m)^\\s*//.*$")
        val problems = mutableListOf<String>()
        val checked = mutableListOf<String>()
        val withSets = subprojects.mapNotNull { q -> q.extensions.findByType<SourceSetContainer>()?.let { q to it.toList() } }
        withSets.forEach { (p, sets) ->
            p.tasks.withType<Test>().toList().forEach { t ->
                val classpath = t.classpath.files
                val own = sets.filter { ss -> ss.output.classesDirs.files.any { it in t.testClassesDirs.files } }
                val onPath = withSets.filter { (q, _) -> classpath.any { it.absolutePath.startsWith(q.layout.buildDirectory.get().asFile.absolutePath + File.separator) } }
                val fixtures = onPath.mapNotNull { (q, qs) -> qs.firstOrNull { it.name == "testFixtures" }?.takeIf { fx ->
                    classpath.any { it in fx.output.classesDirs.files || (it.name.startsWith(q.name) && it.name.contains("test-fixtures")) } } }
                val paths = (own + fixtures).flatMap { it.java.srcDirs }.filter { it.isDirectory }
                    .flatMap { d -> d.walkTopDown().filter { it.isFile && it.name.endsWith(".java") }.toList() }
                    .flatMap { f -> literal.findAll(lineComments.replace(blockComments.replace(f.readText(), ""), "")).map { it.groupValues[1] }.toList() }
                    .filter { it.substringBefore('/') in tops && !it.contains(' ') }
                    .toSortedSet()
                if (paths.isEmpty()) return@forEach
                checked += t.path
                val alwaysRuns = !(t.outputs as org.gradle.api.internal.TaskOutputsInternal).upToDateSpec
                    .isSatisfiedBy(t as org.gradle.api.internal.TaskInternal)
                if (alwaysRuns) return@forEach
                val inputs = t.inputs.files.files.map { it.absolutePath }.toSet()
                val ownPrefixes = own.flatMap { ss -> ss.allSource.srcDirs }.map { it.relativeTo(root).invariantSeparatorsPath + "/" }
                val mainPrefixes = (onPath.map { it.first } + p).map { "${it.projectDir.relativeTo(root).invariantSeparatorsPath}/src/main/" }
                paths.forEach { s ->
                    val f = root.resolve(s)
                    val normalized = s.trimEnd('/') + "/"
                    val covered = when {
                        (ownPrefixes + mainPrefixes).any { normalized.startsWith(it) } -> true
                        !s.contains('/') && f.isDirectory -> inputs.any { it.startsWith(f.absolutePath + File.separator) }
                        f.isFile -> f.absolutePath in inputs
                        f.isDirectory -> f.walkTopDown().filter { it.isFile && it.name != ".DS_Store" }.all { it.absolutePath in inputs }
                        // 없는 경로("여기 없어야 한다"를 단언하는 시험): 가장 가까운 상위 디렉터리가 입력이면 덮였다 — 파일이 생기면 입력이 바뀐다
                        else -> generateSequence(f.parentFile) { it.parentFile }.first { it.exists() }.let { a ->
                            a != root && a.walkTopDown().filter { it.isFile && it.name != ".DS_Store" }.all { it.absolutePath in inputs }
                        }
                    }
                    if (!covered) problems += "${t.path} reads $s"
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("테스트가 읽는 저장소 파일이 그 태스크의 입력이 아니다(바뀌어도 시험이 \"최신\"으로 건너뛴다) — inputs.file/inputs.dir로 선언하라:\n" +
                problems.joinToString("\n"))
        }
        logger.lifecycle("verifyTestInputs: ${checked.size} test tasks read repository files, all declared: ${checked.joinToString(" ")}")
    }
}
tasks.named("check") { dependsOn(verifyTestInputs) }

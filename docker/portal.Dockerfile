# =============================================================================================
# 포털·데모 엔진 대역 이미지(포털 Phase 8 지시문 ②, 계획 Q14·P-16) — 누구나(자격 증명 없이) 빌드한다.
#  ① 플랫폼: 공개 저장소 ga-disclosure의 태그 platform-v0.2.0 소스 묶음(SHA-256 고정)에서 publishToMavenLocal. 포털 빌드는 CI가 아니므로
#     settings.gradle.kts가 mavenLocal()을 쓰고, 받은 파일은 gradle/verification-metadata.xml(GitHub Packages 발행본과 같은 바이트)과
#     같아야 한다 — 다르면 빌드가 실패한다(2026-10-28 이 기계에서 13파일 일치를 확인 — 보고서).
#  ② 포털: :portal-app:bootJar(화면 포함 단일 아티팩트)·:portal-demo:installDist. 시험은 돌리지 않는다(CI가 돈다).
#  ③ 실행 이미지 둘(같은 JRE): portal(target portal), demo(target demo — 엔진 데모 묶음 사본 포함). 사용자 uid 10001(비밀 파일 소유자와 짝).
# 비밀은 이미지에 0 — 실행 때 볼륨 파일로만(compose secrets 서비스).
# =============================================================================================
FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:a9c5ddd386f629518bd500ecfabf7d1e5d32d7ffed735d5c3b1604ae92c830f4 AS build
ADD --checksum=sha256:b7811d0d600fb0cad22e6ec254b0b0047cd88152a3776f6a951cdaae6760918c \
    https://github.com/hjryoo-ai/ga-disclosure/archive/refs/tags/platform-v0.2.0.tar.gz /tmp/platform.tar.gz
RUN mkdir /platform && tar -xzf /tmp/platform.tar.gz -C /platform --strip-components=1 && rm /tmp/platform.tar.gz
WORKDIR /platform
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -q \
      :platform-core:publishToMavenLocal :platform-canonical:publishToMavenLocal :platform-spring:publishToMavenLocal
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain :portal-app:bootJar :portal-demo:installDist \
    && mkdir -p /out \
    && cp "$(ls portal-app/build/libs/*.jar | grep -v -- '-plain.jar$')" /out/portal.jar \
    && cp -r portal-demo/build/install/portal-demo /out/demo

FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:ce8543ddd3070ccbeaac2106a74c9387ddeb8d47a1c43348345715c1ef245c7f AS runtime
RUN groupadd --gid 10001 portal && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin portal

FROM runtime AS demo
COPY --from=build /out/demo /app
COPY contracts/shared/ga-commission-engine/samples/demo /demo/data
USER 10001
EXPOSE 8090
ENTRYPOINT ["/app/bin/portal-demo"]

FROM runtime AS portal
COPY --from=build /out/portal.jar /app/portal.jar
USER 10001
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/portal.jar"]

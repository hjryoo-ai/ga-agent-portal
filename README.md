# ga-agent-portal

GA 소속 설계사가 **"이번 달 얼마 받나 · 이 계약 한도가 얼마 남았나 · 앞으로 매달 얼마 나오나 · 해약되면 얼마 물어내나"**를 스스로 확인하는 읽기 전용 멀티테넌트 포털이다. 수수료의 계산·지급·환수·정산은 별도 저장소 `ga-commission-engine`이 하고, 포털은 엔진 이벤트 스트림을 설계사 관점으로 투영해 **보여주기만** 한다 — 금액을 다시 계산하지 않고(정수 합산·표시용 비율만), 모든 금액에 예상·확정·지급완료 상태를 붙이며, 읽기 모델은 이벤트 seq 0부터 언제든 다시 만들 수 있다. 공용 값객체·테넌트 격리 기반·이벤트 Envelope 핵심은 `ga-disclosure`가 발행하는 플랫폼 아티팩트(`com.ga.platform` 0.2.0)를 쓴다.

> **면책** — 학습·포트폴리오 목적 저장소다. 규제 내용(1200%룰, 분급제 등)은 공개 자료 기반의 예시적 정리이고, 실제 기준은 금융위원회·금융감독원 원문과 유권해석을 따른다. 요율·금액·임계치는 전부 가상의 예시값이다. 이 시스템은 "법령 요건 충족"을 단언하지 않는다.

## 상태

Phase 0(골격) 수용(2026-10-16) — [계획](docs/phase-00-계획.md)·[승인](docs/phase-00-계획승인.md)·[보고서](docs/phase-00-보고서.md)·[수용 심사](docs/phase-00-수용심사.md). 태그 `phase-0`(보고서 1판)·`phase-0.1`(수용 조건 반영). 다음: 엔진 E1(아웃박스)·포털 Phase 1(인제스트). 정본 설계서 [`docs/설계서.md`](docs/설계서.md)(v1.3 — 계약 v1.1), 결정 기록 [`docs/DECISIONS.md`](docs/DECISIONS.md).

## 실행

필요한 것: JDK(툴체인 25는 Gradle이 받는다), Docker(통합 테스트 — 없으면 통합 테스트가 **실패**한다).

```bash
./gradlew build                      # 단위·아키텍처·통합 테스트 + 빌드 검사(SNAPSHOT 0, 플랫폼 SHA 고정, 테스트 입력 선언)
./gradlew printPlatformArtifacts     # 해석된 플랫폼 0.2.0 파일 13개의 경로·SHA-256(인계 §6 표와 대조)
```

플랫폼 아티팩트(`com.ga.platform:*:0.2.0`)는

- **로컬**: `mavenLocal()`에서만 찾는다 — ga-disclosure에서 `./gradlew publishToMavenLocal`(태그 `platform-v0.2.0`)로 넣는다.
- **CI**: GitHub Packages(`hjryoo-ai/ga-disclosure`)에서만 찾는다. 자격 증명은 저장소 비밀 `GHP_USER`·`GHP_READ_TOKEN`(PAT classic, `read:packages`).

어느 쪽이든 받은 파일의 SHA-256이 `gradle/verification-metadata.xml`과 다르면 빌드가 실패한다.

## 인제스트 운영 (Phase 1)

엔진 피드를 테넌트별로 읽는 폴링은 `PORTAL_INGEST_ENABLED=true`일 때만 돈다. 테넌트 목록은 전용 롤 `portal_directory`(`PORTAL_DIRECTORY_USER`·`PORTAL_DIRECTORY_PASSWORD`), 서비스 토큰은 테넌트별 파일 `$PORTAL_ENGINE_TOKEN_DIR/<TENANT_ID>`(소유자만 읽게 — 값은 저장소·로그에 쓰지 않는다). 멈춘 테넌트를 다루는 운영 명령:

```bash
java -jar portal-app.jar --spring.profiles.active=cli ingest status [--tenant=GA01] --actor=ops-kim
java -jar portal-app.jar --spring.profiles.active=cli ingest resume --tenant=GA01 --from-seq=120 --actor=ops-kim
java -jar portal-app.jar --spring.profiles.active=cli ingest pause --tenant=GA01 --actor=ops-kim
```

전 건 `audit_access`에 남는다(거부도). `--from-seq`는 마지막으로 적용한 seq 이하만 — 건너뛰기는 재구축으로만. **인가의 한계**: 조회 API·인증이 오는 Phase 3 전까지 CLI는 실행한 OS 사용자를 믿는다 — 운영 호스트 접근 통제가 곧 인가다.

## 모듈

| 모듈 | 역할 | Phase |
|---|---|---|
| `portal-domain` | 포털 고유 값객체(`CalcId`·`PolicyNo`·`LedgerId`·`ScheduleId`·`CommType`)·상태 열거형. Spring·DB 무의존 | 0 |
| `portal-projection` | 프로젝터(이벤트 → 뷰)·알림 파생 규칙. Spring·DB 무의존 | 2·6 |
| `portal-ingest` | 이벤트 피드 클라이언트·커서·멱등 적용·재구축 | 1 |
| `portal-query` | 조회 API(`/api/v1/me/**`)·인가 | 3 |
| `portal-sim` | 엔진 시뮬레이션 프록시·`simulation_log` | 5 |
| `portal-recon` | 대사·DRIFT·관측 지표 | 7 |
| `portal-infra` | PostgreSQL 어댑터·Flyway·RLS | 0 |
| `portal-app` | Spring Boot 조립·아키텍처 테스트 | 0 |
| `portal-demo` | 데모 테넌트 데이터 생성기 | 8 |
| `web/` | React PWA | 4 |
| `contracts/` | 엔진↔포털 계약(JSON Schema·OpenAPI), `shared/ga-disclosure/` 공유 핵심 사본 | 0 |

## 라이선스

MIT — [`LICENSE`](LICENSE).

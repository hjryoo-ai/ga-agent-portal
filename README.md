> 🌐 **한국어** · [English](README.en.md)

# ga-agent-portal

GA 소속 설계사가 **"이번 달 얼마 받나 · 이 계약 한도가 얼마 남았나 · 앞으로 매달 얼마 나오나 · 해약되면 얼마 물어내나"**를 스스로 확인하는 읽기 전용 멀티테넌트 포털이다. 수수료의 계산·지급·환수·정산은 별도 저장소 `ga-commission-engine`이 하고, 포털은 엔진 이벤트 스트림을 설계사 관점으로 투영해 **보여주기만** 한다 — 금액을 다시 계산하지 않고(정수 합산·표시용 비율만), 모든 금액에 예상·확정·지급완료 상태를 붙이며, 읽기 모델은 이벤트 seq 0부터 언제든 다시 만들 수 있다. 공용 값객체·테넌트 격리 기반·이벤트 Envelope 핵심은 `ga-disclosure`가 발행하는 플랫폼 아티팩트(`com.ga.platform` 0.2.0)를 쓴다.

> **면책** — 학습·포트폴리오 목적 저장소다. 규제 내용(1200%룰, 분급제 등)은 공개 자료 기반의 예시적 정리이고, 실제 기준은 금융위원회·금융감독원 원문과 유권해석을 따른다. 요율·금액·임계치는 전부 가상의 예시값이다. 이 시스템은 "법령 요건 충족"을 단언하지 않는다.

## 상태

Phase 0(골격) 수용(2026-10-16) — [계획](docs/phase-00-계획.md)·[승인](docs/phase-00-계획승인.md)·[보고서](docs/phase-00-보고서.md)·[수용 심사](docs/phase-00-수용심사.md). 태그 `phase-0`(보고서 1판)·`phase-0.1`(수용 조건 반영).

Phase 1(인제스트) 수용(2026-10-17) — [지시문](docs/phase-01-지시문.md)·[계획](docs/phase-01-계획.md)·[승인](docs/phase-01-계획승인.md)·[보고서](docs/phase-01-보고서.md)·[수용 심사](docs/phase-01-수용심사.md). 태그 `phase-1`.

Phase 2(프로젝션) 수용(2026-10-17) — [지시문](docs/phase-02-지시문.md)·[계획](docs/phase-02-계획.md)·[승인](docs/phase-02-계획승인.md)·[보고서](docs/phase-02-보고서.md)·[수용 심사](docs/phase-02-수용심사.md). 태그 `phase-2`.

Phase 3(조회 API) 수용(2026-10-17) — [지시문](docs/phase-03-지시문.md)·[계획](docs/phase-03-계획.md)·[승인](docs/phase-03-계획승인.md)·[보고서](docs/phase-03-보고서.md)·[수용 심사](docs/phase-03-수용심사.md). 태그 `phase-3`.

Phase 4(화면 1차) 수용(2026-10-22, 조건 A1 → `phase-4.1`) — [지시문](docs/phase-04-지시문.md)·[계획](docs/phase-04-계획.md)·[승인](docs/phase-04-계획승인.md)·[보고서](docs/phase-04-보고서.md)·[수용 심사](docs/phase-04-수용심사.md). 태그 `phase-4`·`phase-4.1`.

Phase 5(분급 타임라인·시뮬레이션) 수용(2026-10-23, 조건 B1 → `phase-5.1`) — [지시문](docs/phase-05-지시문.md)·[계획](docs/phase-05-계획.md)·[보고서](docs/phase-05-보고서.md)·[수용 심사](docs/phase-05-수용심사.md). 태그 `phase-5`·`phase-5.1`.

정본 설계서 [`docs/설계서.md`](docs/설계서.md)(v1.8.1), 결정 기록 [`docs/DECISIONS.md`](docs/DECISIONS.md).

## 실행

필요한 것: JDK(툴체인 25는 Gradle이 받는다), Docker(통합 테스트 — 없으면 통합 테스트가 **실패**한다). 화면(`web/`)의 Node 24·pnpm도 Gradle이 받는다 — 따로 설치하지 않는다.

```bash
./gradlew build                      # 단위·아키텍처·통합 테스트 + 빌드 검사(SNAPSHOT 0, 플랫폼 SHA 고정, 테스트 입력 선언)
./gradlew printPlatformArtifacts     # 해석된 플랫폼 0.2.0 파일 13개의 경로·SHA-256(인계 §6 표와 대조)
./gradlew :web:e2e                   # 화면 E2E(Playwright 세 프로젝트, Docker 필요 — 브라우저는 :web:e2eInstall)
```

플랫폼 아티팩트(`com.ga.platform:*:0.2.0`)는

- **로컬**: `mavenLocal()`에서만 찾는다 — ga-disclosure에서 `./gradlew publishToMavenLocal`(태그 `platform-v0.2.0`)로 넣는다.
- **CI**: GitHub Packages(`hjryoo-ai/ga-disclosure`)에서만 찾는다. 자격 증명은 저장소 비밀 `GHP_USER`·`GHP_READ_TOKEN`(PAT classic, `read:packages`).

어느 쪽이든 받은 파일의 SHA-256이 `gradle/verification-metadata.xml`과 다르면 빌드가 실패한다.

## 인제스트 운영 (Phase 1)

엔진 피드를 테넌트별로 읽는 폴링은 `PORTAL_INGEST_ENABLED=true`일 때만 돈다. 테넌트 목록은 전용 롤 `portal_directory`(`PORTAL_DIRECTORY_USER`·`PORTAL_DIRECTORY_PASSWORD`), 서비스 토큰은 테넌트별 파일 `$PORTAL_ENGINE_TOKEN_DIR/<TENANT_ID>`(소유자만 읽게 — 값은 저장소·로그에 쓰지 않는다). 멈춘 테넌트를 다루는 운영 명령:

```bash
java -jar portal-app.jar --spring.profiles.active=cli ingest status [--tenant=GA01]
java -jar portal-app.jar --spring.profiles.active=cli ingest resume --tenant=GA01 --from-seq=120
java -jar portal-app.jar --spring.profiles.active=cli ingest pause --tenant=GA01
java -jar portal-app.jar --spring.profiles.active=cli ingest rebuild --tenant=GA01   # Phase 2 — 설계서 §6.7
java -jar portal-app.jar --spring.profiles.active=cli inquiry list --tenant=GA01 [--status=OPEN]   # Phase 6 — docs/운영.md §7
java -jar portal-app.jar --spring.profiles.active=cli inquiry answer --tenant=GA01 --id=<uuid> --answer-file=<path>   # 답변은 파일로만
java -jar portal-app.jar --spring.profiles.active=cli inquiry close --tenant=GA01 --id=<uuid>
```

전 건 `audit_access`에 남는다(거부도). `--from-seq`는 마지막으로 적용한 seq 이하만 — 건너뛰기는 재구축으로만. **인가(Phase 3)**: 행위자는 실행한 OS 사용자이고, `cli:<OS 사용자>`가 그 테넌트의 `identity_link`에 `OPERATOR`로 등록돼 있어야 한다(등록 SQL은 `docs/운영.md` §3). `--actor`는 없어졌다(종료 코드 64). 거부는 `T not authorized` + 종료 코드 2.

`rebuild`(Phase 2)는 그 테넌트의 뷰를 피드 seq 1부터 **한 트랜잭션**으로 다시 만든다 — 실패하면 아무것도 바뀌지 않고 커서가 `FAILED(REBUILD_FAILED: …)`가 된다(종료 코드 1). 재구축 중에도 조회는 옛 행을 본다. 피드 머리가 `portal.ingest.rebuild.max-events`(기본 50,000)를 넘으면 시작하지 않는다.

## 프로젝션 (Phase 2)

엔진 이벤트 13종을 뷰 9표로 투영한다(설계서 §4.2 표·§5). 금액은 엔진 값을 그대로 담고 포털은 정수 합산만 한다. 골든: `portal-projection/src/test/resources/golden-streams/`(스트림·출처 해시는 그 README)과 `golden-views/`(기대 행 — 손으로 검산). 마감·지급 이벤트의 합계가 포털 합산과 다르면 독이 아니라 그 달 `DRIFT`(지표 `projection_drift_total`).

`earnings_line.basis`의 `reason`처럼 엔진이 주는 자유 문자열은 설계사·고객 값이 아니어서 평문 스캔 대상이 아니다(엔진 규칙 설명) — 저장은 하되 로그·오류 메시지에는 옮기지 않는다.

## 조회 API (Phase 3)

설계사 본인 조회 `/api/v1/me/**`(계약 `contracts/api/v1/portal-api.openapi.yaml`, 인가 표 `docs/authz-matrix.md`). 인증은 테넌트 IdP의 RS256 액세스 토큰 — 테넌트는 토큰 발급자(`iss`)로, 설계사·역할은 `identity_link`로만 정한다(경로·쿼리에 agentId 없음). 거부는 같은 바이트(401 하나·404 하나·쿼리 형식 400 하나). 증권번호는 응답에 없고 `policyMasked`·`policyKey`(배포 비밀 HMAC)만. 운영 등록(발급자·설계사 링크·운영자·`policyKey` 비밀)은 `docs/운영.md`. 배포에 필요한 것: `PORTAL_ISSUER_USER`·`PORTAL_ISSUER_PASSWORD`(전용 롤 `portal_issuer`), `PORTAL_POLICY_KEY_SECRET_FILE`.

실 엔진 E2E(정규 빌드 밖, 손으로 실행): `ENGINE_REPO=<엔진 commission-system 경로> e2e/engine-feed/run.sh` — 엔진 로컬 스택 → 포털(인제스트 켬) → 뷰 9표 = 골든 `a1`, 조회 API 한 건 계약 통과 → `e2e/engine-feed/RESULT.md`. 절차·비밀 처리는 `e2e/engine-feed/README.md`.

## 화면 (Phase 4·5)

`web/` — React 19 + TypeScript + Vite 모바일 웹. 빌드 산출물은 `portal-app` 부트 jar 안에 들어가 API와 **같은 출처**로 서빙된다(CORS 없음, CSP `'self'`만). 화면: 홈 `/`, 월별 명세 `/statements`, 라인 상세 `/lines/{calcId}`, 한도 `/limits`, Phase 5 분급 타임라인 `/deferrals`·시뮬레이션 `/simulations`(엔진 호출은 서버만 — 결과는 "설계사 참고용 · 비확정 · 고객 제시 불가"). 토큰은 웹뷰 호스트가 `/#token=<JWT>`로 넣고 화면은 메모리에만 둔다(새로고침하면 다시 넣어야 한다). 규약은 `CLAUDE.md` "화면 규약", 개발 명령은 [`web/README.md`](web/README.md).

로컬에서 화면을 눈으로 보려면(Docker 필요):

```bash
./gradlew :web:e2eServe              # PostgreSQL·발급자·피드 스텁(GA01C~GA04C, 허구 스트림 전부)·엔진 시뮬레이션 스텁(엔진 샘플 사본) + 포털 — Ctrl-C로 정리
```

로그인 URL(토큰이 든 `/#token=…`)은 터미널에 찍지 않고 `web/build/e2e/.state/login-urls.txt`(디렉터리 700·파일 600, `build/`라 커밋되지 않는다)에만 쓴다. 그 파일의 URL을 브라우저로 연다. 토큰은 하네스의 시험용 발급자가 만든 것이라 3시간 뒤 또는 하네스가 내려가면 쓸 수 없다.

## 모듈

| 모듈 | 역할 | Phase |
|---|---|---|
| `portal-domain` | 포털 고유 값객체(`CalcId`·`PolicyNo`·`LedgerId`·`ScheduleId`·`CommType`)·상태 열거형. Spring·DB 무의존 | 0 |
| `portal-projection` | 프로젝터(이벤트 → 뷰)·알림 파생 규칙. Spring·DB 무의존 | 2·6 |
| `portal-ingest` | 이벤트 피드 클라이언트·커서·멱등 적용·재구축 | 1·2 |
| `portal-query` | 조회 API(`/api/v1/me/**`)·인가 | 3 |
| `portal-sim` | 엔진 시뮬레이션 프록시·`simulation_log` | 5 |
| `portal-recon` | 대사·DRIFT·관측 지표 | 7 |
| `portal-infra` | PostgreSQL 어댑터·Flyway·RLS | 0 |
| `portal-app` | Spring Boot 조립·아키텍처 테스트 | 0 |
| `portal-demo` | 데모 테넌트 데이터 생성기 | 8 |
| `web/` | React 모바일 웹(같은 출처 — 부트 jar 안) | 4 |
| `contracts/` | 엔진↔포털 계약(JSON Schema·OpenAPI), `shared/ga-disclosure/` 공유 핵심 사본 | 0 |

## 라이선스

MIT — [`LICENSE`](LICENSE).

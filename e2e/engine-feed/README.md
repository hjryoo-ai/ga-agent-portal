# e2e/engine-feed — 실 엔진 피드 E2E

포털과 실제 엔진(`ga-commission-engine`)을 잇는 E2E(포털 Phase 3 지시문 D-16, Phase 1 수용 심사 Q6). **정규 빌드 밖**이다 — 엔진 앱·Oracle Free 컨테이너가 포털 빌드 의존이 되지 않게. 사용자가 손으로 실행하고, 결과는 `RESULT.md`(스크립트가 쓴다)와 아래 "재현 기록"에 남긴다. 수용 심사는 이 실행을 기다리지 않는다.

## 실행

```sh
ENGINE_REPO=/path/to/ai-comm/commission-system e2e/engine-feed/run.sh
```

필요한 것: Docker, JDK 25(Gradle 툴체인), `openssl`·`curl`, 엔진 저장소(E2 수용 커밋 이후 — `localstack/up.sh`가 있는 것). 쓰는 포트: 엔진 `8080`·Oracle `1521`(엔진 스크립트), 포털 `8081`(`E2E_PORTAL_PORT`), PostgreSQL `127.0.0.1:55432`(`E2E_PG_PORT`). 끝나면(성공·실패 모두) 포털 프로세스·발급기·PostgreSQL 컨테이너를 내리고 엔진 스택도 `up.sh down`으로 내린다(`E2E_KEEP_ENGINE=1`이면 엔진은 남긴다).

## 순서

1. 엔진 `localstack/up.sh` — Oracle 새 컨테이너 + 시드 + A-1 둘째 판 시나리오 + 엔진 앱(테넌트 `GA01`). 포털은 그 명령을 부를 뿐 내용을 복제하지 않는다.
2. 일회용 PostgreSQL 컨테이너 `ga-portal-e2e-pg` — 이미지는 `gradle/libs.versions.toml`의 `postgres-image`(digest 고정), `docker/postgres/init-roles.sql`, **볼륨 없음**(사용자의 compose 볼륨과 무관).
3. 비밀 파일(`.state/`, 권한 600): 엔진 서비스 토큰 사본 `tokens/GA01`, `policyKey` 비밀 32바이트, 허구 subject `e2e-<무작위>`.
4. 시험 발급기 `./gradlew :portal-app:runTestIssuer --args=<.state>` — 인프로세스 RS256 발급기(`TestIssuer`)가 그 subject의 토큰(대상 `portal-e2e`, 1시간)과 발급자 URL을 `.state/`에 쓴다.
5. 포털 bootJar 기동(`PORTAL_INGEST_ENABLED=true`, Flyway가 V1~V5 적용).
6. 테넌트 `GA01`(`engine_base_url` = 로컬 엔진, `oidc_issuer` = 시험 발급기)·설계사 링크(`A-1001`, `AGENT`)를 **파일 SQL**로 등록(`docs/운영.md` §1·§2와 같은 문장).
7. 따라잡기 대기 — 커서 `caught_up_at`이 생길 때까지(`last_seq = headSeq`·`RUNNING`으로 끝난 회차 = `ingest_lag_seq` 0. actuator는 `health`만 노출하므로 지표 대신 같은 조건을 DB에서 본다).
8. 판정 `./gradlew :portal-app:e2eCheck` → `RESULT.md`:
   - 뷰 9표 = `portal-projection/src/test/resources/golden-views/a1/`(둘째 판 — `ProjectionGoldenIT`와 같은 정규형: `tenant_id` 제외, 시각 UTC). **제외 열 없음** — 엔진 `up.sh verify`가 피드를 계약 사본 `a1_portal_150000_e2.jsonl`과 바이트 비교하므로 `seq`·`eventId`·`occurredAt`도 같아야 한다(자리 단계 README의 "제외 규칙"은 필요 없어졌다).
   - `GET /api/v1/me/earnings/202608`(시험 발급기 토큰)이 200, 계약 `contracts/api/v1/portal-api.openapi.yaml` 통과, 증권번호 원문 없음.

`ingest rebuild` 뒤 재대조(자리 단계 README의 4번)는 넣지 않았다 — D-16 범위 밖이고, 재구축 = 골든은 `RebuildIT`가 계약 사본으로 증명한다. 운영자 링크(`cli:<OS 사용자>`)를 등록하는 단계가 늘어 실행 표면만 커진다.

## 비밀·개인정보

토큰·subject·비밀은 `.state/` 파일에만 있고(`.gitignore`), 셸 인자·환경 변수·출력에 싣지 않는다. 포털에 넘기는 환경 변수는 경로(`PORTAL_ENGINE_TOKEN_DIR`·`PORTAL_POLICY_KEY_SECRET_FILE`)와 DB URL뿐이다. PostgreSQL 슈퍼유저 비밀번호는 무작위 값으로 `.state/pg.env`(`--env-file`)에만. 포털 롤 자격 증명은 `init-roles.sql`의 로컬 기본값(일회용 컨테이너 안에서만). `RESULT.md`에는 날짜·커밋·해시·행 수만 쓴다. 실패하면 `.state/portal.log`·`.state/issuer.log`를 본다 — 다음 실행이 `.state/`를 지운다.

## 재현 기록

(없음 — 포털 Phase 3 시점 미실행. 실행하면 `RESULT.md`를 커밋하고 여기에 한 줄: 일시, 엔진·포털 커밋, 판정)

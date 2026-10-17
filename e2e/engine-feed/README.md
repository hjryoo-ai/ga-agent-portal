# e2e/engine-feed — 실 엔진 피드 E2E (자리)

Phase 1 수용 심사 Q6의 결정: 포털과 실제 엔진(`ga-commission-engine`)을 잇는 E2E는 **정규 빌드 밖**의 이 디렉터리에 둔다 — 엔진 앱·Oracle Free 컨테이너가 포털 빌드 의존이 되지 않게. 사용자가 손으로 실행하고, 결과는 아래 "재현 기록"에 남긴다.

## 상태

**스크립트 없음.** 엔진 E2의 "로컬 스택 한 명령 기동"(엔진 앱 + Oracle Free 컨테이너 + 시드 + 서비스 토큰)이 수용된 뒤 포털 Phase 3 0단계에서 쓴다(Phase 2 지시문 7단계 — "자리만, 스크립트는 E2 뒤").

## 예정 내용

1. 엔진 로컬 스택을 엔진 저장소의 명령으로 기동한다(포털은 그 명령을 부를 뿐 내용을 복제하지 않는다).
2. 포털을 `PORTAL_INGEST_ENABLED=true`로 기동한다 — 테넌트 행의 `engine_base_url`은 로컬 엔진, 서비스 토큰은 `$PORTAL_ENGINE_TOKEN_DIR/<TENANT_ID>` 파일(값은 이 저장소·로그·셸 인자에 쓰지 않는다).
3. 엔진에서 A-1 시나리오(월납 150,000)를 실행하고, 포털 뷰를 `portal-projection/src/test/resources/golden-views/a1/`과 대조한다(`seq`·`eventId`·`occurredAt`이 다를 수 있는 열은 제외 규칙을 스크립트에 적는다).
4. `ingest rebuild` 뒤 같은 대조를 다시 한다.

## 재현 기록

(없음)

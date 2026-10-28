# 포털 Phase 8 지시문 — 데모 테넌트·통합 실행 (ga-agent-portal, v1.0, 2026-10-28) — 마지막 Phase

## 형식에 대해

E2 형식 — **범위·완료 기준·질문만**. 지시문 문장이 코드·계약·엔진과 다르면 P-번호로 반증한다. 계획 승인은 사용자가 직접 해도 된다. 돌이킬 수 없는 결정 후보: **엔진 쪽 데모 스트림 생성 커밋**(⓪), **새 의존성**(OTLP 내보내기), **CSP 변경**(질문 2), **이벤트 계약 표기 1.4.1**(DD-19) — 계획에 있으면 승인 전에 보낸다.

브랜치 `work/phase-8`, 태그 `phase-8`, PR, **병합은 수용 심사 회신 뒤**. 병합 뒤 릴리스 태그 `v1.0`(질문 9). 정본: `docs/설계서.md` v1.10(§3.2, §9 인증, §11, §12 Phase 8, §13 성능, §14.1 #14, 부록 B·C), Phase 7 수용 심사 §2·§3(F11 불변식, OTLP, DD-19, 스케줄러 그대로), `CLAUDE.md` 전 절.

## 0. 전제

- 포털 `main` = Phase 7 병합 뒤. 이 Phase는 **새 도메인 기능이 없다.** 하는 것은 ① 남은 결함 둘(재구축 끝 불변식 F11, 계약 설명문 DD-19) ② 누구나 `docker compose up`으로 띄워 **다섯 화면 전부에 데이터가 보이는** 데모 ③ 독립 브라우저 로그인(Keycloak + PKCE — §14.1 #14 닫기) ④ 관측 스택(OTLP → Jaeger) ⑤ 성능 측정(k6, §13) ⑥ 포트폴리오 README(부록 C) + 문서 정리. 설계서 §12 완료 기준 그대로: **"`docker compose up` 후 데모 설계사로 로그인해 5개 화면 모두 데이터 표시."**
- **데모 데이터도 엔진이 만든다.** 부록 B는 "생성기 → JSONL → 파일 피드"라고 했지만, 포털 안의 생성기가 수수료·세액·한도를 계산하면 규칙 1 위반이다(`portal-demo` main도 `MultiplicationScanTest`의 9모듈 안이다 — 계획이 확인). 그러므로 데모 스트림은 **엔진이 자기 파이프라인으로 낸 아웃박스 골든**(A-1 `a1_portal_150000_e2.jsonl`과 같은 경로 — E1·E2의 골든 러너)이고, 포털은 사본을 `contracts/shared/ga-commission-engine/samples/demo/`에 둔다. 시뮬레이션·명세 샘플도 **데모 설계사용**으로 같은 엔진 커밋에서(스텁은 샘플 밖 요청에 500이므로 데모 설계사의 요청 쌍이 있어야 한다). 포털의 `portal-demo`는 **시드·등록·파일 피드 어댑터**만(계산 0).
- 데모는 두 프로파일: **`demo`**(필수 — 엔진 없음: 포털 + PostgreSQL + Keycloak + 파일 피드 + 샘플 스텁(시뮬레이션·명세) + Jaeger)와 **`engine`**(선택 — 엔진 `localstack`(Oracle Free, digest 고정)을 compose에 붙여 `run.sh` 판정 다섯을 compose 안에서; 질문 1에서 가능성을 재고 안 되면 범위 밖으로 기록).
- 보안 규약 전부 그대로: 토큰 메모리만, 증권번호 원문 0, 서비스 토큰·HMAC 비밀은 compose가 **처음 뜰 때 생성**해 볼륨의 파일(600)에만(저장소에 비밀 0 — 평문 스캔 그대로), Keycloak 관리자 비밀번호도 생성값(로그에 0). 데모 설계사의 로그인 비밀번호만 README에(허구 계정).
- `RESULT.md`(실 엔진 판정 다섯)는 여전히 사용자 몫 — `engine` 프로파일이 되면 그 안에서 한 번 돌려 커밋.

## 1. 범위

⓪ **0단계 — 둘**: (a) 이벤트 계약 표기 `contracts/events/v1` **1.4.1** — `StatementEstimated.gross` 설명문 "도래 예정 미리보기 포함, `basisSeq`는 하한"(DD-19, 스키마 제약 무변경 — 파싱 동치 시험) + `CHECKSUMS` + 엔진 사본 갱신 PR(선승인 — 엔진 CI 뒤 직접 병합, main 소스 0). (b) **엔진 데모 스트림 커밋**(엔진 저장소, 시험 코드·샘플만 — 골든 러너 시나리오): 부록 B의 **필수 케이스 전부**(한도 80/95/100% 각 1+, CUT·DEFER_AFTER_FY 각 1, 재산정 1, HELD 1, 채권 이월 1, 취소 짝 1, 마감 후 ADJUSTMENT 1) + 설계사 수·계약 수는 **엔진 골든 러너가 감당하는 만큼**(부록 B의 30명·400건은 목표치 — 계획이 재서 줄이면 §14.1에 기록; 하한 설계사 5·계약 40, 세 조직 경로), 2026-06-30/07-01·2027-01-01 경계 양쪽, 24개월 분량. 같은 커밋에 데모 설계사용 시뮬레이션·명세 샘플(스텁 대조용 — 설계사마다 신계약 1·해약 1·명세 두 달). `UPSTREAM`·`CHECKSUMS` 포털 사본, `EnvelopeValidator`·프로젝션 불변식·`NotificationStreamsTest`·`ReconRules`를 **데모 스트림 전체에** 돌리는 시험(골든 뷰 — `-Pgolden.update=true`, 손 검산 요약).

① **재구축 끝 불변식(F11)**: `Rebuilder` txB 끝 — "`recon_result`의 마지막 행이 미해결 `DRIFT`인 (설계사, 달)은 `data_status = DRIFT`"를 **복원**(읽기만 — 파생 아님, Phase 6·7 ArchUnit 무변경). 운영자 재구축 뒤 화면이 "정상"으로 돌아가지 않는다. `RebuildIT` 확장 + E2E ⓔ 변형(운영자 재구축 뒤 "확인 중" 유지). `docs/운영.md` §8 문단은 "불변식이 복원한다"로.

② **`portal-demo` + compose `demo` 프로파일**: `docker/compose.yaml`(digest 고정 이미지 전부 — PostgreSQL 18·Keycloak·Jaeger·포털은 로컬 빌드 이미지), 처음 뜰 때 ⓐ 비밀 생성(서비스 토큰·HMAC·Keycloak admin) ⓑ Flyway ⓒ 테넌트 "데모GA"(`oidc_issuer` = compose의 Keycloak 렐름, 파라미터는 설계서 예시값) ⓓ `identity_link`(데모 설계사 전원 — subject = Keycloak 사용자 id, 역할 AGENT + 운영자 1) ⓔ 파일 피드 어댑터(엔진 `/internal/v1/events` 계약 **그대로**를 섬기는 작은 서버 — 하네스의 `StubFeedServer`를 제품화하지 말고 `portal-demo`에 같은 계약의 구현, 머리를 시간에 따라 올리는 "재생 속도" 옵션으로 화면에서 동기화 칩이 움직이는 것을 보일 수 있게) ⓕ 샘플 스텁(시뮬레이션·명세 — Phase 5·7 스텁과 같은 대조 규약, 샘플 밖 500). 포털 앱은 운영 설정 그대로(데모 전용 코드 경로 0 — 프로파일은 compose 설정값뿐). 한 명령: `docker compose --profile demo up` → README의 주소·계정으로 로그인 → 다섯 화면.

③ **Keycloak + PKCE(§14.1 #14)**: 렐름 파일(`docker/keycloak/realm.json` — 공개 클라이언트, PKCE S256 필수, 리다이렉트 = 포털 출처, 데모 사용자 N명 + 비밀번호는 README 허구값, `aud`·`iss` = 포털 `tenant.oidc_*`), 웹에 **독립 브라우저 로그인 경로**: 토큰 없이 열면 "다시 로그인" 화면의 버튼이 **PKCE 인가 요청**으로(프래그먼트 주입 경로는 그대로 — 둘 다 `src/boot/token.ts`에 모인다), 콜백 `/auth/callback?code&state`는 `code`를 메모리에서 교환하고 주소에서 지운다(토큰·`code`·`state`는 저장소 0 — `state`·`code_verifier`는 메모리; 새 탭·새로고침이면 다시 로그인), 만료 전 갱신은 **refresh 토큰 메모리 보관 + 회전**(ID/액세스/refresh 전부 메모리, Keycloak 설정 refresh 회전·짧은 수명), 401이면 버리고 로그인 화면. 발급자 주소는 `/me` 전 호출이라 토큰 없이 알아야 한다 — **서버가 `index.html` 응답 헤더 또는 `/auth/config`(인증 없음, 발급자 주소·클라이언트 id만)**로 준다(질문 2). CSP `connect-src`에 발급자 출처(테넌트 설정에서 — 하드코딩 0), 그 외 CSP 그대로. 웹뷰 호스트 경로의 토큰 갱신(#14 본문)은 "호스트가 PKCE 경로를 쓸 수도 있다"로 닫는다.

④ **관측 스택**: `opentelemetry-exporter-otlp`(Boot BOM — 검증 메타데이터 승인 대상) + compose의 Jaeger(OTLP 수신), 내보내기는 속성으로만 켬(시험·CI 프로파일 꺼짐). compose E2E가 Jaeger API에서 `recon.run`·`ingest.apply` 스팬 하나씩 읽어 **속성 키 ⊆ `docs/지표.md` 표**인지 본다(Phase 7 수용 Q2). Prometheus 긁기 끝점(`/actuator/prometheus` — `portal_health` 롤·DB 헬스는 Phase 0 규약대로 허용 목록 조건을 만족할 때만 켠다, 아니면 DB 헬스 끈 채) + compose의 Prometheus(선택 — 질문 5).

⑤ **성능(§13)**: k6 스크립트(`perf/k6/home.js`) — 데모 설계사 N명(토큰은 Keycloak에서 미리 받아 메모리 — 파일 0) × 홈 다섯 콜, 마감 익일 09시 시나리오, p95 목표 §10(화면 p95 — 설계서 값). compose에서 돌리고 결과 표를 보고서에(CI에는 넣지 않는다 — 환경 의존). ETag 표식 문장(Phase 7 P7-15)이 p95에 보이는지 한 줄.

⑥ **compose E2E**: Playwright 프로젝트 하나(데스크톱 Chromium — 세 프로젝트는 하네스 E2E가 이미 덮는다)가 **compose 스택을 상대로** Keycloak 로그인 → 홈·명세·한도·타임라인·시뮬레이션 다섯 화면 데이터 표시 → 알림함·문의 접수 → `recon run`(compose exec) → DRIFT 데모(라인 누락은 하네스 전용 — compose에서는 **`auto_rebuild_enabled` 끈 채 스트림 자체의 DRIFT 케이스**를 데모 스트림에 하나 넣어 보인다 — 엔진 커밋 ⓪에 "확정 합 불일치" 케이스는 엔진이 낼 수 없으므로 **포털 손 편집 스트림이 아니라** 운영자 재구축 뒤 표식 유지(①)로 대체 — 질문 4) → 로그아웃. 누출·CSP·axe 고정 장치 그대로(토큰·센티널 = 데모 증권번호 전부). CI 잡 `compose-e2e`(Docker 필요, `no-docker`는 실패).

⑦ **`run.sh` → compose `engine` 프로파일**(가능하면): 엔진 `localstack`의 Oracle Free + 엔진 앱을 compose 서비스로, 포털 `tenant.engine_base_url`을 그쪽으로, `run.sh`를 `docker compose --profile engine run e2e` 한 명령으로. 판정 다섯 → `RESULT.md`. 안 되면(이미지 크기·라이선스·시간) 이유를 적고 범위 밖.

⑧ **문서·마무리**: README(부록 C 다섯 + "AI 협업 방법론" 절 — 설계서 → 지시문 → 계획 → 승인 → 증거 기반 수용, DE/DD/RM 수와 뜻, Phase 표 + 태그·PR·CI run), `README.en.md`·`docs/en/` **최종 동기화 한 번**(질문 8), 설계서 v1.11(§3.2 compose·Keycloak·Jaeger, §9 PKCE·CSP `connect-src`, §11 스택, §12 전 Phase 완료 표시, §13 k6 결과, §14.1 #14 닫기 + 남은 미결정 표 정리 — "v2"로 남는 것 목록), `docs/운영.md`(compose 운영·비밀 생성·Keycloak), `docs/DECISIONS.md` 색인(DE 1~29·DD 1~20·RM 1~4·P-결정 Phase별), `CLAUDE.md` "데모·배포 규약" 절(데모 코드 경로 0·비밀 생성·프로파일), 릴리스 태그 `v1.0`.

## 2. 완료 기준

| # | 기준 | 증명 |
|---|---|---|
| C1 | 이벤트 계약 1.4.1 = 표기만(파싱 동치), 엔진 사본 갱신 병합, 데모 스트림·샘플 사본 = 엔진 바이트 | `EventSchemaTest` 파싱 동치, `verifyUpstreamContract`·`verifyContractChecksums` |
| C2 | 데모 스트림이 부록 B 필수 케이스 전부를 담는다(표 — 케이스 ↔ seq), 계약 검증 통과, 프로젝션 불변식·결정론 통과, 골든 뷰 커밋(손 검산 요약), 알림·대사 규칙이 스트림 전체에서 예외 0 | 시험 + 보고서 표 |
| C3 | **포털 저장소에 수수료·세액·한도 계산 코드 0** — `portal-demo` main 포함 9모듈 `MultiplicationScanTest`·`ReconArithmeticScanTest` 허용 목록 무변경 | 스캔 무변경 diff |
| C4 | 재구축 끝 불변식(F11): 운영자 재구축 뒤 미해결 BATCH DRIFT 달의 `data_status = DRIFT` 유지, ArchUnit(재구축 → 파생 0) 무변경 | `RebuildIT` 확장, E2E, 주입: 복원 제거 |
| C5 | `docker compose --profile demo up` 한 명령 → README 계정 로그인 → **다섯 화면 전부 데이터**(홈·명세·한도·타임라인·시뮬레이션) + 알림함·문의 | `compose-e2e` 잡 + 보고서 화면 상태 표 |
| C6 | 비밀은 compose가 생성해 볼륨 파일(600)에만 — 저장소·이미지·로그·`docker compose config` 출력에 0 | 평문 스캔 + compose E2E의 로그 스캔, 주입: 비밀을 `.env`에 |
| C7 | PKCE: `state`·`code_verifier`·토큰·refresh 전부 메모리, 주소에서 `code`·`state` 제거, 저장소 5종 비어 있음, 갱신 회전, 401 → 로그인 화면; 프래그먼트 주입 경로 무손상(하네스 E2E 그대로 통과) | Vitest + compose E2E, 주입: `code_verifier`를 `sessionStorage`에 |
| C8 | CSP: `connect-src`에 발급자 출처만 추가(설정값), 나머지 지시어 그대로, 위반 0, 외부 요청 = 발급자뿐 | `WebScreensIT` 확장 + E2E |
| C9 | OTLP 내보내기는 속성으로만, 시험·CI 꺼짐; Jaeger의 스팬 속성 키 ⊆ 표; 새 의존성 diff 추가만 | compose E2E + 검증 메타데이터 diff |
| C10 | k6 결과 표(p50/p95/p99, 콜별), 목표 대비 판정, ETag 표식 문장 영향 한 줄 | 보고서(CI 밖) |
| C11 | Phase 0~7 무손상(전 시험·주입 규약), 하네스 E2E 그대로 통과, `no-docker` 실패, 번들 상한 유지(PKCE 코드는 지연 청크 또는 상한 안 — 넘으면 사유·승인) | CI 아티팩트 |
| C12 | 문서: README 부록 C·방법론 절, 영문 최종 동기화, 설계서 v1.11 §12 전 Phase 완료·§14.1 정리, DECISIONS 색인, `v1.0` 태그(병합 뒤) | 보고서 |

## 3. 계획에서 답할 질문

1. **엔진 골든 러너가 데모 규모를 감당하나** — 먼저 재라. 설계사·계약 수를 얼마까지, 24개월 이벤트가 몇 건인지(재구축 상한 50,000 안인지), 러너 실행 시간. 줄이면 부록 B 목표치와 실제값을 §14.1에.
2. **발급자 주소를 화면이 아는 방법**: ⓐ `/auth/config`(인증 없음 — 발급자·클라이언트 id만, 캐시 가능) ⓑ `index.html`의 `<meta>`(서버가 테넌트 설정으로 채움 — 다중 테넌트면 호스트명으로 고른다) ⓒ 빌드 시 고정(다중 테넌트 불가). 권장 **ⓐ** — 다만 "인증 없는 끝점"이 하나 생기니 거부 바이트 표·`AuthzMatrixTest`에 어떻게 넣을지. CSP `connect-src`도 같은 값에서.
3. **refresh 토큰을 둘지**: 메모리 회전(권장 — 세션이 탭 수명) vs 액세스 토큰만(만료 때마다 재로그인). Keycloak 수명 예시값.
4. **compose에서 DRIFT를 어떻게 보일지**: 라인 누락 제어는 하네스 전용이다. ⓐ 데모 스트림에 엔진이 낼 수 없는 "확정 합 불일치"를 넣지 않는다 → compose E2E는 운영자 재구축 뒤 표식 유지(①)와 `recon run` MATCH만 ⓑ `portal-demo`에 "데모 전용 DB 조작" 명령(라인 삭제) — 제품 코드에 시험 경로가 생긴다(지금까지 0). 권장 **ⓐ**, DRIFT 감지·복구의 증거는 Phase 7 하네스 E2E가 이미 있다고 README에.
5. **Prometheus를 compose에 넣을지**: Jaeger만(권장 — 지표는 `/actuator/prometheus` 긁기 끝점까지) vs Prometheus + Grafana까지.
6. **`engine` 프로파일의 Oracle Free**: 이미지 크기·기동 시간·라이선스 표기(Oracle Free는 재배포 조건) — 재고 가능하면 넣고, 아니면 `run.sh` 그대로 두고 README에 절차.
7. **데모 설계사 로그인 비밀번호**: README 허구값 하나(전원 같음)인지 생성값(처음 뜰 때 출력 — 로그에 남는다)인지. 권장 README 허구값(데모).
8. **영문 문서 최종 동기화**: 마지막 커밋에 한 번(권장) — README.en·design.en·`e2e/engine-feed/README.en`. 이후는 요청 시만.
9. **릴리스 태그 `v1.0`**: 병합 커밋에 사용자가 단다(권장) — 태그 메시지에 Phase 표와 CI run. `phase-8` 태그는 보고서 커밋.
10. **남는 v2 목록**(§14.1 정리): 조직장 뷰(#9)·PWA·다이제스트·페이지(`/me/limits`)·운영 화면·푸시/알림톡·Phase 9 — 설계서 §12 Phase 9 줄과 §14.1을 한 표로 합칠지.

## 4. 하지 말 것

포털 안의 수수료·세액·한도 계산(데모 생성기 포함). 데모 전용 제품 코드 경로(프로파일은 설정값뿐). 저장소의 비밀·`.env`의 비밀. 토큰·`code_verifier`의 저장소. 하드코딩 발급자 주소. CSP의 다른 지시어 완화. Phase 0~7 규칙 시험 약화. 새 도메인 기능. 설계서 §12 Phase 9 항목.

## 5. 보고 형식

Phase 7과 같게. 추가: 데모 스트림 케이스 표(부록 B ↔ seq·설계사), 데모 규모 실측(질문 1), 다섯 화면 상태 표(compose E2E 스크린 상태 — 스냅샷 아님), PKCE 흐름의 저장 위치 표(값마다 "메모리/없음"), CSP 최종 헤더, k6 표, 새 의존성 diff, compose 기동 시간·이미지 digest 표, `RESULT.md` 상태(⑦ — 실행했으면 판정 다섯 결과), 영문 동기화 범위, **마지막 보고서이므로** Phase 0~8 총괄 표(시험 수 추이·주입 수·DE/DD/RM 수)와 남은 v2 목록.

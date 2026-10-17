# 포털 Phase 3 계획 승인 (2026-10-17) — ga-agent-portal

## 판정: 승인. Q1~Q12 전부 권장안. 지시문 오류 일곱 건 확정(DE-12~18). 조건은 §2의 작은 것들뿐

읽은 것: `work/phase-3` = `301926a`의 `docs/phase-03-계획.md` 전문. 플랫폼 0.2.0의 `ResolvedIdentity`가 `orgPath` 공백에 예외를 던진다는 것(F2)과 A-1 둘째 판에 `AgentSnapshot`이 없다는 것(F3)은 내가 지시문을 쓰며 확인했어야 했다.

## 1. 인정하는 것

- **Q3 — 유휴 폴링은 `updated_at`을 바꾸지 않는다.** 글자대로면 이벤트 없는 밤마다 모든 테넌트가 `SYNC_DELAYED`가 됐다. `caught_up_at`은 엔진 다운과 밀림을 **둘 다** 잡고 `asOf.at`의 뜻을 지킨다 — 지시문 D-6보다 나은 정의다.
- **Q5 — "비가역"이 틀렸다.** 증권번호 형식이 좁아 SHA-256은 열거로 되돌아가고, 키는 쿼리 문자열로 로그에 남는다. HMAC + 배포 비밀 파일이 맞다.
- **Q2 — 두 사실의 교차**: OPERATOR에는 설계사가 없고(V1 `NOT NULL`), 플랫폼은 `orgPath`를 요구하는데 A-1에는 `AgentSnapshot`이 없어 `agent_profile`에서 읽으면 **A-1 설계사 전원이 404**였을 것이다. 설계서 §14.1 #9를 근거로 `identity_link.org_path`에 둔 것이 맞다.
- **Q6 — 테넌트를 모르는 401은 감사 행을 쓸 곳이 없고, 등록된 `iss`만 실은 위조 토큰이 감사 표를 채울 수 있다**(쓰기 증폭). 401은 지표만, 404는 감사 — 가른 기준이 정확하다.
- ② 검증 순서를 **여덟** 사유로 세고 전부 한 바이트로 묶은 것, ③의 "토큰에서 읽는 것은 다섯 클레임뿐 — `Jwt` 멤버 호출 클래스 셋의 닫힌 목록"을 ArchUnit으로 고정하는 것, Q4의 ETag 분석(같은 `seq`에서도 응답이 바뀐다), ⑤의 문장 수를 엔드포인트별로 미리 센 것.

## 2. 결정

| # | 결정 | 조건 |
|---|---|---|
| Q1 | **`portal_issuer` 전용 롤 — 허용 목록 첫 항목** | 항목 = FQN `com.ga.portal.infra.directory.IssuerDirectoryReader` + 사유("발급자 표는 테넌트 설정 — 테넌트 바인딩 전에 읽어야 한다"). 세 열 밖·다른 표 권한 오류(`IssuerDirectoryIT`, M15). 발급자 값은 로그에 **해시**(URL도 식별자다 — D-14 확장). |
| Q2 | **`agent_id` NULL은 OPERATOR만, `identity_link.org_path`** | `agent_profile.org_path`(엔진 사실 — 표시용)와 `identity_link.org_path`(포털 사실 — 인가용, v2)는 **다른 출처**임을 설계서 §9에 한 줄 — 둘이 다르면 어느 쪽도 고치지 않는다(v2 결정). |
| Q3 | **`caught_up_at`** | 갱신은 "피드 호출 성공 **그리고** `last_seq = headSeq` **그리고** 커서 `RUNNING`"일 때만, 잠금 안 짧은 트랜잭션. 피드 실패·정지 상태에서는 건드리지 않는다(M12). |
| Q4 | **`W/"<seq>.<sync>.<cfg>"`** | `cfg` 입력은 그 요청이 **실제로 읽은** 설정만(엔드포인트마다 다를 수 있다 — 표로). 소유 확인 뒤 비교(남의 자원은 404). |
| Q5 | **HMAC-SHA256(배포 비밀 파일) 앞 16자** | 비밀 회전 시 모든 `policyKey`가 바뀐다 — `docs/운영.md`에 적고 화면(Phase 4)은 키를 저장하지 않는다. 키 → 원문 해석은 **요청 안에서 그 설계사의 증권번호 집합**으로만(SQL에 키 없음). |
| Q6 | **`audit_access.detail`, 404 감사 / 401 지표** | `detail`은 닫힌 사유 코드만(값 0). 지표 `tenant` 태그는 발급자가 등록된 경우만. |
| Q7 | 평평한 라인 + `reversalOf`·`reversedBy`, 접기는 Phase 4 | 정렬 `source_seq, calc_id`. |
| Q8 | 표식은 유스케이스 메서드(ga-disclosure 방식) | ArchUnit ⓐⓑⓒ 그대로. 지시문 D-11 문구는 오류. |
| Q9 | 본인 이름 그대로, 프로필 없으면 생략 200 | DD-13. |
| Q10 | 전 테넌트 `status`는 테넌트마다 링크, 하나라도 거부면 종료 코드 2 | `--actor`는 64. |
| Q11 | `from`·`to` 필수, ≤ 36개월, 400 한 바이트 | — |
| Q12 | n > 그 달 일수 → 생략 + 경고 | — |
| F6 | **`trim_scale(...)::text`** | 지시문 D-10 오류. |
| F7·F8·F9 | 승인 | 새 의존성은 Boot BOM 셋(`security`·`oauth2-resource-server`, 전이 `nimbus-jose-jwt`) — 검증 메타데이터 **재생성 diff**를 보고서에(의도 외 아티팩트 0). `limit 0` → `utilization` 생략. |
| ②-6 허용 오차 60초 | 승인 | 시계는 주입(`JwtTimestampValidator.setClock`) — `TZ=UTC` 검사에 포함. |
| 주입 M1~M16 | 승인 | — |

## 3. 지시문 오류 (DECISIONS)

| # | 지시문 | 무엇이 틀렸나 |
|---|---|---|
| DE-12 | D-1 | 발급자 열을 읽을 롤을 정하지 않았다 — `portal_directory`는 못 읽는다고 하면서(Q1). |
| DE-13 | D-2·D-9·D-10 | `identity_link.agent_id NOT NULL`과 OPERATOR, 플랫폼 `orgPath` 필수와 빈 `agent_profile`(Q2). |
| DE-14 | D-6 | 유휴 폴링이 `updated_at`을 바꾸지 않는다(Q3). |
| DE-15 | D-7 | "비가역" — 비밀 없는 해시는 열거로 되돌아간다(Q5). |
| DE-16 | D-8 | ETag가 시간·설정 의존을 빠뜨렸다(Q4). |
| DE-17 | D-3 | `audit_access`에 `detail`이 없고, 테넌트 없는 401은 행을 쓸 곳이 없다(Q6). |
| DE-18 | D-10·D-11 | `USING limit_multiple::text`(`"12.00"`), 표식을 컨트롤러에(ga-disclosure와 다름). |

일곱 건 — 포털 지시문도 E1 수준이다. 포털 설계서가 내 것이라 결정 형식을 유지했지만, **Phase 4부터 포털도 E2 형식**(범위·완료 기준·질문만, 계획이 설계)으로 간다. 설계서 결함 **DD-13**(§7 `/me` "마스킹된 이름").

## 4. 절차

승인 보관 + 계획 갱신 1커밋(§2 조건: 발급자 해시 로그, 두 `org_path` 출처 문장, `caught_up_at` 조건, 검증 메타데이터 diff) → 2~8단계. 보고서·`phase-3` 태그·PR까지, 병합은 수용 심사 뒤. E2E(`run.sh`)는 사용자 실행 — 보고서에 "미실행"이면 그렇게.

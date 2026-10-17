# 시험 스트림 (포털 Phase 1 계획 ⑧)

JSONL — 첫 줄 머리 `{"tenantId", "schemaVersion"}`, 다음 줄부터 Envelope 한 줄씩(`FileFeedAdapter` 형식). 전부 계약 샘플
`contracts/events/v1/samples/01~17`(허구 데이터)에서 만들었고, `StreamFilesTest`가 아래 표의 규칙으로 다시 계산해 파일과 대조한다 — 샘플이
바뀌면 그 시험이 실패한다.

| 파일 | 테넌트 | 내용 | 쓰는 시험 |
|---|---|---|---|
| `a1-golden.jsonl` | GA01 | 샘플 1~17 | C13 결정론, IT 기본 |
| `dup.jsonl` | GA01 | 1, 2, 3, **3**, 4, 5 (같은 줄 두 번) | C1 |
| `replay-known.jsonl` | GA01 | 1~5 뒤 **3** 다시 | C2 건너뜀 |
| `replay-new.jsonl` | GA01 | 1~5 뒤 seq 3에 **새 eventId** `…0000000000f3` | C2 독 `PAST_SEQ_NEW_EVENT` |
| `gap.jsonl` | GA01 | 1~4, **6**~17 | C3 `SEQ_GAP` |
| `poison-schema.jsonl` | GA01 | 1~4, 5에서 `payload.accumAfter` 제거, 6~17 | C6 `SCHEMA_VIOLATION` |
| `poison-kind.jsonl` | GA01 | 1~4, 5의 `aggregate.kind` = `POLICY`, 6~17 | C6 `TYPE_KIND_MISMATCH` |
| `hash-mismatch.jsonl` | GA01 | 1~5 뒤 5와 같은 eventId·seq, `payload.accumAfter` − 1 | C7 `HASH_MISMATCH` |
| `version2.jsonl` | GA01 | 1~4, 5의 `version` = 2, 6~17 | C8 `UNSUPPORTED_VERSION` |
| `other-tenant.jsonl` | **GA02** | 샘플 1~17 | C9 `TENANT_MISMATCH`(GA01이 읽으면) |

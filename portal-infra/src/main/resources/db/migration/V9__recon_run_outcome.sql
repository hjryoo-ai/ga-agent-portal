-- =============================================================================================
-- V9__recon_run_outcome.sql — 포털 Phase 7. V8의 ck_recon_run_outcome 고침(커밋된 마이그레이션은 고치지 않는다 — 새 V로만, CLAUDE.md 6).
--
-- V8은 "REPAIRED는 성공한 재구축 뒤에만"을 (result <> 'REPAIRED' OR rebuild_outcome = 'SUCCEEDED')로 썼다. rebuild_outcome이 NULL이면
-- 둘째 항이 NULL이라 식 전체가 NULL — CHECK는 NULL을 통과시킨다. 재구축 없는 REPAIRED 행이 들어갔다(ReconRunSchemaIT 거부 사례 24가
-- CI run 40490868281에서 잡았다). NULL을 값으로 보는 IS NOT DISTINCT FROM으로 다시 쓴다. 운영 행 0(V8과 같은 Phase — 대사가 아직 돌지 않았다).
-- =============================================================================================

ALTER TABLE recon_run DROP CONSTRAINT ck_recon_run_outcome;
ALTER TABLE recon_run ADD CONSTRAINT ck_recon_run_outcome CHECK ((result = 'FAILED') = (rebuild_outcome IS NOT DISTINCT FROM 'FAILED')
                                                                 AND (result <> 'REPAIRED' OR rebuild_outcome IS NOT DISTINCT FROM 'SUCCEEDED'));

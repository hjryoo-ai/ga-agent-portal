package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.InquiryRecord;
import com.ga.portal.domain.InquiryStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code inquiry}의 운영 CLI 문장(포털 Phase 6 ⑦ — {@code inquiry list|answer|close}): 테넌트 전체 범위(운영자는 설계사가 아니다). 모든 문장이
 * {@code tenant_id = :tenantId}를 가진다({@code TenantPredicateScanTest}). 설계사 범위의 문장은 {@link InquiryRepository} — 알림함 API는 이 클래스에 닿지 않는다
 * ({@code ApiLayerRulesTest} g). 답변·종료는 상태를 조건으로 건 한 문장이라 앞의 잠금 읽기와 어긋나지 않는다. 지우는 문장은 없다.
 */
@Repository
public class InquiryOperatorRepository extends ViewRepository {

    public InquiryOperatorRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> p = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            p.put((String) kv[i], kv[i + 1]);
        }
        return p;
    }

    /** 테넌트의 문의 — 정렬 {@code created_at, inquiry_id}(접수 순), 상태 필터는 선택. */
    public List<InquiryRecord> list(Optional<InquiryStatus> status) {
        return query("""
                SELECT inquiry_id, agent_id, calc_id, category, message, status, created_at, answered_at, answer, answered_by
                  FROM inquiry
                 WHERE tenant_id = :tenantId AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
                 ORDER BY created_at, inquiry_id
                """, params("status", status.map(Enum::name).orElse(null)), InquiryRepository.MAPPER);
    }

    /** 한 문의를 잠그고 읽는다(답변·종료의 같은 트랜잭션 — 동시 답변 둘이 둘 다 통과하지 않게). */
    public Optional<InquiryRecord> lockOne(UUID id) {
        return queryAtMostOne("""
                SELECT inquiry_id, agent_id, calc_id, category, message, status, created_at, answered_at, answer, answered_by
                  FROM inquiry
                 WHERE tenant_id = :tenantId AND inquiry_id = :id
                   FOR UPDATE
                """, params("id", id), InquiryRepository.MAPPER);
    }

    /** {@code OPEN → ANSWERED}. 바뀐 행 수(0이면 이미 OPEN이 아니다). */
    public int answer(UUID id, String answer, String answeredBy, Instant at) {
        return update("""
                UPDATE inquiry SET status = 'ANSWERED', answer = :answer, answered_by = :answeredBy, answered_at = :at
                 WHERE tenant_id = :tenantId AND inquiry_id = :id AND status = 'OPEN'
                """, params("id", id, "answer", answer, "answeredBy", answeredBy, "at", utc(at)));
    }

    /** {@code OPEN|ANSWERED → CLOSED}. 바뀐 행 수. */
    public int close(UUID id) {
        return update("""
                UPDATE inquiry SET status = 'CLOSED'
                 WHERE tenant_id = :tenantId AND inquiry_id = :id AND status IN ('OPEN', 'ANSWERED')
                """, params("id", id));
    }

    /** 열린 문의의 수와 가장 오래된 접수 시각(포털 Phase 7 — 운영 {@code status}·지표 {@code inquiry_open_*}). 본문·설계사는 읽지 않는다. */
    public record OpenSummary(long count, Optional<Instant> oldest) {
    }

    public OpenSummary openSummary() {
        return queryAtMostOne("""
                SELECT count(*) AS n, min(created_at) AS oldest FROM inquiry WHERE tenant_id = :tenantId AND status = 'OPEN'
                """, Map.of(), (rs, n) -> new OpenSummary(rs.getLong("n"), optInstant(rs, "oldest"))).orElse(new OpenSummary(0, Optional.empty()));
    }
}

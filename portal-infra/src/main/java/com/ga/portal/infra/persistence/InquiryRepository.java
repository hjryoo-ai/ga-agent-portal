package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.InquiryCategory;
import com.ga.portal.domain.InquiryRecord;
import com.ga.portal.domain.InquiryStatus;
import com.ga.portal.domain.rule.Paging;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code inquiry}(설계서 §5 V7, 포털 Phase 6 ⑥): 설계사의 접수·목록·상세와 레이트 리밋 창·직렬화 잠금, 문의의 라인 확인. 모든 문장이 {@code tenant_id = :tenantId}와
 * 그 설계사({@code :agentId})를 가진다({@code TenantPredicateScanTest}). 운영 CLI의 문장(테넌트 전체 목록·답변·종료)은 {@code InquiryOperatorRepository}에 따로 —
 * 이 파일은 설계사 범위만이다. 지우는 문장은 없다.
 */
@Repository
public class InquiryRepository extends ViewRepository {

    public record Window(long count, Optional<Instant> oldest) {
    }

    static final RowMapper<InquiryRecord> MAPPER = (rs, n) -> new InquiryRecord(rs.getObject("inquiry_id", UUID.class), rs.getString("agent_id"),
            optString(rs, "calc_id"), InquiryCategory.valueOf(rs.getString("category")), rs.getString("message"), InquiryStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"), optInstant(rs, "answered_at"), optString(rs, "answer"), optString(rs, "answered_by"));

    public InquiryRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    private static Map<String, Object> params(String agentId, Object... more) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentId", agentId);
        for (int i = 0; i < more.length; i += 2) {
            p.put((String) more[i], more[i + 1]);
        }
        return p;
    }

    /** 그 설계사의 라인인지(문의의 {@code calcId} — 남의 라인은 없는 라인과 같다). */
    public boolean ownsLine(String agentId, String calcId) {
        return queryAtMostOne("""
                SELECT true AS owned FROM earnings_line WHERE tenant_id = :tenantId AND agent_id = :agentId AND calc_id = :calcId
                """, params(agentId, "calcId", calcId), (rs, n) -> rs.getBoolean("owned")).orElse(false);
    }

    /**
     * 설계사별 문의 접수 직렬화(트랜잭션 수준 권고 잠금 — 리밋 판정과 넣기가 한 덩어리). 키 공간 {@code hashtext('portal.inquiry')}는 인제스트·시뮬레이션과 겹치지
     * 않고, 둘째 키는 {@code 테넌트 ∥ U+001F ∥ 설계사}.
     */
    public void lock(String agentId) {
        queryAtMostOne("""
                SELECT true AS locked FROM pg_advisory_xact_lock(hashtext('portal.inquiry'), hashtext(:tenantId || chr(31) || :agentId)) AS l
                """, params(agentId), (rs, n) -> rs.getBoolean("locked"));
    }

    /** {@code created_at > since}인 그 설계사의 문의 수(상태 무관)와 가장 이른 시각. */
    public Window window(String agentId, Instant since) {
        return queryAtMostOne("""
                SELECT count(*) AS n, min(created_at) AS oldest
                  FROM inquiry
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND created_at > :since
                """, params(agentId, "since", utc(since)), (rs, n) -> new Window(rs.getLong("n"), optInstant(rs, "oldest")))
                .orElse(new Window(0, Optional.empty()));
    }

    public void insert(InquiryRecord row) {
        update("""
                INSERT INTO inquiry (tenant_id, inquiry_id, agent_id, calc_id, category, message, status, created_at)
                VALUES (:tenantId, :id, :agentId, :calcId, :category, :message, 'OPEN', :createdAt)
                """, params(row.agentId(), "id", row.id(), "calcId", orNull(row.calcId()), "category", row.category().name(), "message", row.message(),
                "createdAt", utc(row.createdAt())));
    }

    /** 한 페이지 — 정렬 {@code created_at DESC, inquiry_id DESC}, 상태 필터는 선택. */
    public List<InquiryRecord> page(String agentId, Optional<InquiryStatus> status, int page, int size) {
        return query("""
                SELECT inquiry_id, agent_id, calc_id, category, message, status, created_at, answered_at, answer, answered_by
                  FROM inquiry
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
                 ORDER BY created_at DESC, inquiry_id DESC
                 LIMIT :size OFFSET :offset
                """, params(agentId, "status", status.map(Enum::name).orElse(null), "size", size, "offset", Paging.offset(page, size)), MAPPER);
    }

    public long count(String agentId, Optional<InquiryStatus> status) {
        return queryAtMostOne("""
                SELECT count(*) AS n
                  FROM inquiry
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
                """, params(agentId, "status", status.map(Enum::name).orElse(null)), (rs, n) -> rs.getLong("n")).orElse(0L);
    }

    public Optional<InquiryRecord> find(String agentId, UUID id) {
        return queryAtMostOne("""
                SELECT inquiry_id, agent_id, calc_id, category, message, status, created_at, answered_at, answer, answered_by
                  FROM inquiry
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND inquiry_id = :id
                """, params(agentId, "id", id), MAPPER);
    }
}

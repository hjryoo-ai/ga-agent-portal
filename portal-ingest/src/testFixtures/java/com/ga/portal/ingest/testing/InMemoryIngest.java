package com.ga.portal.ingest.testing;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.TenantTransactions;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * 인메모리 인제스트 저장소·트랜잭션·잠금(단위 시험용 — 실제 PostgreSQL 구현과 같은 포트). 트랜잭션은 시작 때 그 테넌트 상태를 복사해 두고
 * 예외면 되돌린다(롤백). 바인딩된 테넌트는 플랫폼 {@link TenantContext}(앱 구현과 같다). 단일 스레드 시험용.
 */
public final class InMemoryIngest implements IngestStore, TenantTransactions, IngestLock {

    private record State(Optional<CursorRecord> cursor, Map<String, IngestEventRecord> events, Optional<Instant> caughtUpAt) {
        State(Optional<CursorRecord> cursor, Map<String, IngestEventRecord> events) {
            this(cursor, events, Optional.empty());
        }

        State copy() {
            return new State(cursor, new LinkedHashMap<>(events), caughtUpAt);
        }
    }

    private final Map<TenantId, State> tenants = new HashMap<>();
    private boolean lockHeldElsewhere;
    private int transactions;
    private RuntimeException failNextMarkApplied;

    /** 다음 {@link #markApplied} 한 번이 이 예외를 던진다(DB 제약 위반 같은 분류 밖 오류 흉내). */
    public void failNextMarkApplied(RuntimeException e) {
        this.failNextMarkApplied = e;
    }

    /** 다른 워커가 잠금을 쥔 것처럼. */
    public void holdLockElsewhere(boolean held) {
        this.lockHeldElsewhere = held;
    }

    public int transactions() {
        return transactions;
    }

    @Override
    public <T> T inTransaction(TenantId tenant, Supplier<T> work) {
        transactions++;
        State before = state(tenant).copy();
        try {
            return TenantContext.runWith(tenant, work::get);
        } catch (RuntimeException e) {
            tenants.put(tenant, before);
            throw e;
        } catch (Exception e) {
            tenants.put(tenant, before);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean tryLock() {
        TenantContext.current();
        return !lockHeldElsewhere;
    }

    private State state(TenantId tenant) {
        return tenants.computeIfAbsent(tenant, t -> new State(Optional.empty(), new LinkedHashMap<>()));
    }

    private State bound() {
        return state(TenantContext.current());
    }

    @Override
    public Optional<CursorRecord> cursor() {
        return bound().cursor();
    }

    @Override
    public CursorRecord ensureCursor(Instant now) {
        State s = bound();
        if (s.cursor().isEmpty()) {
            CursorRecord created = new CursorRecord(0, Optional.empty(), IngestState.RUNNING, now, Optional.empty());
            tenants.put(TenantContext.current(), new State(Optional.of(created), s.events(), s.caughtUpAt()));
            return created;
        }
        return s.cursor().get();
    }

    @Override
    public void saveCursor(long lastSeq, Optional<String> lastEventId, IngestState state, Optional<String> error, Instant now) {
        tenants.put(TenantContext.current(), new State(Optional.of(new CursorRecord(lastSeq, lastEventId, state, now, error)), bound().events(),
                bound().caughtUpAt()));
    }

    @Override
    public Optional<IngestEventRecord> event(String eventId) {
        return Optional.ofNullable(bound().events().get(eventId));
    }

    @Override
    public void markApplied(IngestEventRecord record) {
        if (failNextMarkApplied != null) {
            RuntimeException e = failNextMarkApplied;
            failNextMarkApplied = null;
            throw e;
        }
        IngestEventRecord existing = bound().events().get(record.eventId());
        if (existing != null && existing.status() != IngestEventStatus.FAILED) {
            throw new IllegalStateException("duplicate key (tenant_id, event_id): " + record.eventId());
        }
        bound().events().put(record.eventId(), record);
    }

    @Override
    public void markFailed(IngestEventRecord record) {
        IngestEventRecord existing = bound().events().get(record.eventId());
        if (existing != null && existing.status() == IngestEventStatus.APPLIED) {
            throw new IllegalStateException("an APPLIED row is never overwritten: " + record.eventId());
        }
        bound().events().put(record.eventId(), record);
    }

    @Override
    public boolean markCaughtUp(long headSeq, Instant at) {
        State s = bound();
        boolean ok = s.cursor().filter(c -> c.state() == IngestState.RUNNING && c.lastSeq() == headSeq).isPresent();
        if (ok) {
            tenants.put(TenantContext.current(), new State(s.cursor(), s.events(), Optional.of(at)));
        }
        return ok;
    }

    @Override
    public Optional<Instant> caughtUpAt() {
        return bound().caughtUpAt();
    }

    /** 시험이 보는 따라잡은 시각. */
    public Optional<Instant> caughtUpAtOf(TenantId tenant) {
        return state(tenant).caughtUpAt();
    }

    @Override
    public OptionalLong maxAppliedSeq() {
        return bound().events().values().stream().filter(e -> e.status() == IngestEventStatus.APPLIED).mapToLong(IngestEventRecord::seq).max();
    }

    // ------------------------------------------------------------------ 시험이 보는 것

    public Optional<CursorRecord> cursorOf(TenantId tenant) {
        return state(tenant).cursor();
    }

    public List<IngestEventRecord> eventsOf(TenantId tenant) {
        return List.copyOf(state(tenant).events().values());
    }
}

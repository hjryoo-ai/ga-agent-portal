package com.ga.portal.infra.persistence;

import com.ga.platform.core.tenant.TenantNotBoundException;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.AgentStatus;
import com.ga.portal.domain.DeferralStatus;
import com.ga.portal.domain.FlagType;
import com.ga.portal.domain.MonthStatus;
import com.ga.portal.domain.ReceivableStatus;
import com.ga.portal.domain.ReconSource;
import com.ga.portal.domain.view.AgentRow;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.FlagRow;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerRow;
import com.ga.portal.domain.view.LineRow;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.domain.view.ReceivableRow;
import com.ga.portal.domain.view.ReconRow;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.RecordingDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C1(지시문 v1.2): TenantContext 미바인딩 상태에서 포털 저장소의 모든 공개 메서드는 DB에 닿기 전에 실패한다(커넥션 획득 0) —
 * 플랫폼 0.2.0의 보장을 포털 저장소로 다시 증명한다. 데이터소스는 어떤 호출이든 실패하는 가짜를 기록기로 감싼 것이다(Docker 불요).
 */
class TenantContextGuardTest {

    /** 커넥션을 달라는 순간 실패하는 데이터소스 — DB에 닿았다면 테스트가 다른 예외로 실패한다. */
    private static final DataSource UNREACHABLE = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                throw new AssertionError("database reached: " + method.getName());
            });

    private final RecordingDataSource recording = new RecordingDataSource(UNREACHABLE);
    private final TenantRepository tenants = new TenantRepository(new TenantJdbcGateway(recording));
    private final IngestCursorRepository cursors = new IngestCursorRepository(new TenantJdbcGateway(recording));
    private final IngestEventRepository events = new IngestEventRepository(new TenantJdbcGateway(recording));
    private final IngestLockRepository locks = new IngestLockRepository(new TenantJdbcGateway(recording));
    private final AuditAccessRepository audit = new AuditAccessRepository(new TenantJdbcGateway(recording));
    // Phase 2 뷰 저장소
    private final AgentProfileRepository agents = new AgentProfileRepository(new TenantJdbcGateway(recording));
    private final EarningsLineRepository lines = new EarningsLineRepository(new TenantJdbcGateway(recording));
    private final EarningsMonthRepository months = new EarningsMonthRepository(new TenantJdbcGateway(recording));
    private final PolicyLimitRepository limits = new PolicyLimitRepository(new TenantJdbcGateway(recording));
    private final DeferralItemRepository deferrals = new DeferralItemRepository(new TenantJdbcGateway(recording));
    private final ClawbackItemRepository receivables = new ClawbackItemRepository(new TenantJdbcGateway(recording));
    private final PolicyFlagRepository flags = new PolicyFlagRepository(new TenantJdbcGateway(recording));
    private final ReconResultRepository recon = new ReconResultRepository(new TenantJdbcGateway(recording));
    private final ViewResetRepository viewReset = new ViewResetRepository(new TenantJdbcGateway(recording));
    // Phase 3
    private final IdentityLinkRepository links = new IdentityLinkRepository(new TenantJdbcGateway(recording));
    private final AgentQueryRepository query = new AgentQueryRepository(new TenantJdbcGateway(recording));
    private final SyncStatusRepository sync = new SyncStatusRepository(new TenantJdbcGateway(recording));
    // Phase 5
    private final SimulationLogRepository simLog = new SimulationLogRepository(new TenantJdbcGateway(recording));
    private final SimulationLockRepository simLock = new SimulationLockRepository(new TenantJdbcGateway(recording));
    // Phase 6
    private final NotificationRepository notifications = new NotificationRepository(new TenantJdbcGateway(recording));
    private final InquiryRepository inquiries = new InquiryRepository(new TenantJdbcGateway(recording));
    private final InquiryOperatorRepository operatorInquiries = new InquiryOperatorRepository(new TenantJdbcGateway(recording));
    // Phase 7
    private final ReconLockRepository reconLock = new ReconLockRepository(new TenantJdbcGateway(recording));
    private final ReconRunRepository reconRuns = new ReconRunRepository(new TenantJdbcGateway(recording));
    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");
    private static final IngestEventRecord ROW = new IngestEventRecord(TenantId.of("GA01"), "5eed0000-0000-4000-8000-000000000001", 1,
            "AgentSnapshot", NOW, IngestEventStatus.APPLIED, Optional.empty(), "d".repeat(64));

    /** 저장소에 공개 메서드가 늘면 이 목록과 아래 호출을 함께 늘린다(새 메서드가 시험 밖에 남지 않게). */
    @Test
    void theGuardedMethodsAreAllPublicMethodsOfTheRepositories() {
        assertThat(publicMethods(TenantRepository.class)).containsExactly("current");
        assertThat(publicMethods(IngestCursorRepository.class)).containsExactly("caughtUpAt", "ensure", "find", "markCaughtUp", "save");
        assertThat(publicMethods(IngestEventRepository.class)).containsExactly("find", "markApplied", "markFailed", "maxAppliedSeq");
        assertThat(publicMethods(IngestLockRepository.class)).containsExactly("tryLock");
        assertThat(publicMethods(AuditAccessRepository.class)).containsExactly("recent", "record", "recordDenial");
        assertThat(publicMethods(AgentProfileRepository.class)).containsExactly("find", "save");
        assertThat(publicMethods(EarningsLineRepository.class)).containsExactly("find", "insert", "sums");
        assertThat(publicMethods(EarningsMonthRepository.class)).containsExactly("find", "findByMonths", "markDataStatus", "save");
        assertThat(publicMethods(PolicyLimitRepository.class)).containsExactly("find", "findByPolicy", "insert", "insertHistory", "save");
        assertThat(publicMethods(DeferralItemRepository.class)).containsExactly("find", "insert", "save");
        assertThat(publicMethods(ClawbackItemRepository.class)).containsExactly("find", "save");
        assertThat(publicMethods(PolicyFlagRepository.class)).containsExactly("find", "save");
        assertThat(publicMethods(ReconResultRepository.class)).containsExactly("driftAndRepaired", "insertIfAbsent");
        assertThat(publicMethods(ViewResetRepository.class)).containsExactly("deleteViews", "violations");
        assertThat(publicMethods(ViewRepository.class)).isEmpty();
        assertThat(publicMethods(IdentityLinkRepository.class)).containsExactly("find");
        assertThat(publicMethods(AgentQueryRepository.class)).containsExactly("byType", "deferralPolicies", "deferrals", "deferralsOfLine", "dueMonths", "history",
                "ledger", "ledgerIdOfPolicy", "ledgers", "line", "linePolicies", "lines", "month", "monthDetail", "months", "policies", "profile", "receivableOfLine");
        assertThat(publicMethods(SyncStatusRepository.class)).containsExactly("snapshot");
        assertThat(publicMethods(SimulationLogRepository.class)).containsExactly("failed", "insertRequested", "succeeded", "window");
        assertThat(publicMethods(SimulationLockRepository.class)).containsExactly("lock");
        assertThat(publicMethods(NotificationRepository.class)).containsExactly("counts", "insertIfAbsent", "markRead", "page");
        assertThat(publicMethods(InquiryRepository.class)).containsExactly("count", "find", "insert", "lock", "ownsLine", "page", "window");
        assertThat(publicMethods(InquiryOperatorRepository.class)).containsExactly("answer", "close", "list", "lockOne", "openSummary");
        assertThat(publicMethods(ReconLockRepository.class)).containsExactly("tryLock");
        assertThat(publicMethods(ReconRunRepository.class)).containsExactly("insert", "last", "lastRebuild", "rebuilds", "rebuiltBetween", "scheduledBetween",
                "update");
    }

    /** 위 목록이 이 패키지의 저장소 전부다 — 새 저장소가 목록 밖에 남지 않는다(Phase 3: 손으로 적은 목록을 패키지와 대조). */
    @Test
    void theListedRepositoriesAreEveryRepositoryInThePackage() throws Exception {
        // 운영 클래스가 실린 곳(같은 이름의 시험 패키지가 아니라 TenantRepository의 코드 원천 — 디렉터리 또는 jar)
        java.nio.file.Path location = java.nio.file.Path.of(TenantRepository.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String packagePath = TenantRepository.class.getPackageName().replace('.', '/');
        java.util.Set<String> found = new java.util.TreeSet<>();
        try (java.nio.file.FileSystem jar = java.nio.file.Files.isDirectory(location) ? null : java.nio.file.FileSystems.newFileSystem(location)) {
            java.nio.file.Path dir = (jar == null ? location : jar.getPath("/")).resolve(packagePath);
            assertThat(java.nio.file.Files.isDirectory(dir)).as("%s", dir).isTrue();
            try (var files = java.nio.file.Files.list(dir)) {
                for (String name : files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".class") && !n.contains("$")).toList()) {
                    Class<?> type = Class.forName(TenantRepository.class.getPackageName() + "." + name.substring(0, name.length() - ".class".length()));
                    if (com.ga.platform.spring.jdbc.TenantScopedRepository.class.isAssignableFrom(type)) {
                        found.add(type.getSimpleName());
                    }
                }
            }
        }
        assertThat(found).containsExactlyInAnyOrder("TenantRepository", "IngestCursorRepository", "IngestEventRepository", "IngestLockRepository",
                "AuditAccessRepository", "AgentProfileRepository", "EarningsLineRepository", "EarningsMonthRepository", "PolicyLimitRepository",
                "DeferralItemRepository", "ClawbackItemRepository", "PolicyFlagRepository", "ReconResultRepository", "ViewResetRepository",
                "ViewRepository", "IdentityLinkRepository", "AgentQueryRepository", "SyncStatusRepository", "SimulationLogRepository",
                "SimulationLockRepository", "NotificationRepository", "InquiryRepository", "InquiryOperatorRepository", "ReconLockRepository",
                "ReconRunRepository");
    }

    @Test
    void everyRepositoryMethodFailsBeforeReachingTheDatabaseWhenUnbound() {
        assertThatThrownBy(tenants::current).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(cursors::find).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> cursors.save(1, Optional.empty(), IngestState.RUNNING, Optional.empty(), NOW))
                .isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> cursors.ensure(NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> cursors.markCaughtUp(7, NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(cursors::caughtUpAt).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> events.find(ROW.eventId())).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> events.markApplied(ROW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> events.markFailed(new IngestEventRecord(ROW.tenantId(), ROW.eventId(), 1, "AgentSnapshot", NOW,
                IngestEventStatus.FAILED, Optional.of("SCHEMA_VIOLATION: x"), ROW.payloadHash()))).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(events::maxAppliedSeq).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(locks::tryLock).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> audit.record("cli:ops", "INGEST_STATUS", "cursor:GA01@0", NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> audit.recent(5)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> audit.recordDenial("sub", "AUTHZ_DENIED", "GET /api/v1/me", "NO_LINK", NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> links.find("sub")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(viewReset::deleteViews).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(viewReset::violations).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.profile("A-1001")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.months("A-1001", "202601", "202612")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.month("A-1001", "202608")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.byType("A-1001", "202608")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.lines("A-1001", "202608", Optional.empty(), Optional.empty(), 0, 50)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.linePolicies("A-1001", "202608")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.line("A-1001", "1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.deferralsOfLine("A-1001", "1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.receivableOfLine("A-1001", "1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.ledgerIdOfPolicy("A-1001", "P")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.ledgers("A-1001")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.ledger("A-1001", "1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.history("A-1001", "1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.deferrals("A-1001", "202601", "202612", Optional.empty())).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.dueMonths("A-1001", "202601", "202612", Optional.empty())).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.monthDetail("A-1001", "202608", "202607")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.deferralPolicies("A-1001")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(sync::snapshot).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> query.policies("A-1001")).isInstanceOf(TenantNotBoundException.class);
        java.util.UUID simId = java.util.UUID.fromString("00000000-0000-4000-8000-000000000001");
        assertThatThrownBy(() -> simLog.window("A-1001", NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> simLog.insertRequested(simId, "A-1001", "NEW_CONTRACT", "{}", NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> simLog.succeeded(simId, "A-1001", "{}", "RS-1", 200)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> simLog.failed(simId, "A-1001", "ENGINE_CONTRACT", Optional.of(400))).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> simLock.lock("A-1001")).isInstanceOf(TenantNotBoundException.class);
        java.util.UUID id = java.util.UUID.fromString("00000000-0000-4000-8000-000000000002");
        assertThatThrownBy(() -> notifications.page("A-1001", false, 0, 20)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> notifications.counts("A-1001", true)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> notifications.markRead("A-1001", id, NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> notifications.insertIfAbsent(new NotificationRepository.Fresh(id, "A-1001", "PAYOUT", "INFO", "PAYOUT:202608",
                Optional.of("/statements/202608"), "{\"ym\":\"202608\"}", NOW))).isInstanceOf(TenantNotBoundException.class);
        com.ga.portal.domain.InquiryRecord inquiry = com.ga.portal.domain.InquiryRecord.open(id, "A-1001", Optional.empty(),
                com.ga.portal.domain.InquiryCategory.OTHER, "m", NOW);
        assertThatThrownBy(() -> inquiries.ownsLine("A-1001", "1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> inquiries.lock("A-1001")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> inquiries.window("A-1001", NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> inquiries.insert(inquiry)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> inquiries.page("A-1001", Optional.empty(), 0, 20)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> inquiries.count("A-1001", Optional.empty())).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> inquiries.find("A-1001", id)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> operatorInquiries.list(Optional.empty())).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> operatorInquiries.lockOne(id)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> operatorInquiries.answer(id, "a", "cli:ops", NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> operatorInquiries.close(id)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(operatorInquiries::openSummary).isInstanceOf(TenantNotBoundException.class);
        ReconRunRepository.Run run = new ReconRunRepository.Run(NOW, "recon:GA01", com.ga.portal.domain.ReconTrigger.SCHEDULE, 0, 0, 0, 0, 0, 0,
                com.ga.portal.domain.ReconRunResult.OK, Optional.empty(), Optional.empty());
        assertThatThrownBy(reconLock::tryLock).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> reconRuns.insert(run)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> reconRuns.update(run)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> reconRuns.scheduledBetween(NOW, NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> reconRuns.rebuiltBetween(NOW, NOW)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(reconRuns::rebuilds).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(reconRuns::last).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(reconRuns::lastRebuild).isInstanceOf(TenantNotBoundException.class);
        AgentRow agent = new AgentRow("A-1001", "n", Optional.empty(), "/HQ", Optional.empty(), AgentStatus.ACTIVE, LocalDate.of(2026, 8, 1), 1);
        LineRow line = new LineRow("1", "A-1001", "P", Optional.empty(), "FY_COMM", 1, "202608", Optional.empty(), 0, Optional.empty(), "{}", "{}", 2);
        MonthRow month = MonthRow.first("A-1001", "202608", MonthStatus.PROVISIONAL, 2);
        LedgerRow ledger = new LedgerRow("1", "A-1001", "P", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31), 1, "12",
                12, 0, 0, 0, 0, "1");
        HistoryRow history = new HistoryRow("1", 3, Optional.of(1L), "POST", Optional.of("1"), Optional.of(1L), 1, 12, Optional.empty(), NOW);
        DeferralRow deferral = new DeferralRow("S1", "A-1001", "P", "1", "202708", 1, "INFORCE", DeferralStatus.SCHEDULED, "CURVE", "CV1",
                Optional.empty(), 4);
        ReceivableRow receivable = new ReceivableRow("R1", "A-1001", "1", 1, 1, ReceivableStatus.OPEN, 5);
        FlagRow flag = new FlagRow("P", "A-1001", FlagType.CANCELLED, LocalDate.of(2027, 2, 15), Optional.empty(), Optional.of("CANCEL"));
        ReconRow reconRow = new ReconRow("A-1001", "202608", NOW, 1, 0, 0, Optional.of(2L), Optional.empty(), Optional.empty(), Optional.empty(),
                "DRIFT", ReconSource.EVENT, 6);
        assertThatThrownBy(() -> agents.find("A-1001")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> agents.save(agent)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> lines.find("1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> lines.insert(line)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> lines.sums("A-1001", "202608")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> months.find("A-1001", "202608")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> months.save(month)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> months.findByMonths(java.util.List.of("202608"))).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> months.markDataStatus("A-1001", "202608", com.ga.portal.domain.DataStatus.DRIFT)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> limits.find("1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> limits.findByPolicy("P", "A-1001")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> limits.insert(ledger)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> limits.save(ledger)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> limits.insertHistory(history)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> deferrals.find("S1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> deferrals.insert(deferral)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> deferrals.save(deferral)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> receivables.find("R1")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> receivables.save(receivable)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> flags.find("P", "A-1001")).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> flags.save(flag)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> recon.insertIfAbsent(reconRow)).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(recon::driftAndRepaired).isInstanceOf(TenantNotBoundException.class);
        assertThat(recording.connectionsObtained()).isZero();
        assertThat(recording.statements()).isEmpty();
    }

    @Test
    void anUnboundTransactionCannotEvenStart() {
        TransactionTemplate tx = new TransactionTemplate(new TenantSessionBinder(recording));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> tenants.current())).isInstanceOf(TenantNotBoundException.class);
        assertThat(recording.connectionsObtained()).isZero();
    }

    private static String[] publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .map(Method::getName).sorted().toArray(String[]::new);
    }
}

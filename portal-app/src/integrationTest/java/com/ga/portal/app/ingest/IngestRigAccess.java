package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.projection.ViewProjector;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.util.List;

/** 다른 패키지의 IT(포털 Phase 7 — {@code app.recon})가 인제스트 IT 조립({@link IngestRig}·{@link ViewRig})을 쓰는 문. 같은 부품을 그대로 내준다. */
public final class IngestRigAccess {

    public static final PostgresHarness DB = IngestRig.DB;
    public static final Clock CLOCK = IngestRig.CLOCK;

    private final IngestRig rig = new IngestRig();

    public TenantSessionBinder binder() {
        return rig.binder;
    }

    public TenantJdbcGateway gateway() {
        return rig.gateway;
    }

    public IngestStore store() {
        return rig.store;
    }

    public IngestLock lock() {
        return rig.lock;
    }

    /** 파생 워커의 적용기가 쓸 스팬 포트. */
    public IngestRigAccess tracing(com.ga.portal.domain.trace.Tracing tracing) {
        rig.tracing = tracing;
        return this;
    }

    public static TenantTransactions transactions(IngestRigAccess access) {
        return access.rig.transactions;
    }

    /** 실제 뷰 프로젝터 + 운영 파생기(알림)로 도는 워커. */
    public IngestWorker derivingWorker(FeedClient feed) {
        return rig.derivingWorker(feed, 200);
    }

    public static FeedClient feed(TenantId t, List<ObjectNode> events) {
        return ViewRig.feed(t, events);
    }

    public static ViewProjector projector(TenantJdbcGateway gateway) {
        return ViewRig.projector(gateway);
    }

    public static void seedTenant(TenantId t, String engineBaseUrl, String params) {
        IngestRig.seedTenant(t, engineBaseUrl, params);
    }

    public static void operator(TenantId t, String subject) {
        IngestRig.operator(t, subject);
    }

    public static List<String> notificationRows(TenantId t) {
        return IngestRig.notificationRows(t);
    }
}

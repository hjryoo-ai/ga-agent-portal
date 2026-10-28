package com.ga.portal.infra.directory;

import com.ga.platform.core.tenant.TenantId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 토큰의 {@code iss} → 테넌트(포털 Phase 3 D-1, 계획 승인 Q1). 테넌트를 <b>바인딩하기 전에</b> 읽어야 하므로 애플리케이션 데이터소스({@code portal_app},
 * FORCE RLS)가 아니라 <b>전용 롤 {@code portal_issuer}</b>로 따로 접속한다 — V5가 이 롤에 {@code tenant}의 세 열({@code tenant_id}·{@code oidc_issuer}·
 * {@code oidc_audience}) SELECT와 전용 정책만 주었다. 다른 열·표는 권한 오류다({@code IssuerDirectoryIT}). 포털 Phase 8(V10): 같은 롤에 로그인 설정
 * 두 열({@code web_origin}·{@code oidc_client_id})을 더했다 — {@link #byWebOrigin}(인증 없는 {@code /auth/config}·화면 CSP).
 *
 * <p>포털 직접 DB 접근 허용 목록의 첫 항목(아키텍처 규칙 — 닫힌 FQN). 요청마다 고유 인덱스 조회 1회, <b>캐시 없음</b>(발급자 해제가 즉시 효력).
 * 연결은 이 리더만의 작은 풀(읽기 전용) — 데이터소스를 빈으로 등록하지 않는다(애플리케이션 데이터소스와 섞이지 않게).
 * 발급자 값은 로그에 쓰지 않는다(쓰려면 해시 — D-14 확장).
 */
public final class IssuerDirectoryReader implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcClient client;

    public IssuerDirectoryReader(String jdbcUrl, String username, String password, int maxConnections) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Objects.requireNonNull(jdbcUrl, "jdbcUrl"));
        config.setUsername(Objects.requireNonNull(username, "username"));
        config.setPassword(password);
        config.setMaximumPoolSize(maxConnections);
        config.setMinimumIdle(0);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setPoolName("portal-issuer");
        // 기동 시 연결을 열지 않는다 — DB가 늦게 떠도 앱 기동은 실패하지 않고 첫 요청이 실패한다(500, 401이 아니다)
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
        this.client = JdbcClient.create(dataSource);
    }

    /** {@code iss} 정확 일치 하나(UNIQUE). 없으면 빈 값 — 등록되지 않은 발급자. */
    public Optional<IssuerRegistration> find(String issuer) {
        Objects.requireNonNull(issuer, "issuer");
        List<IssuerRegistration> rows = client.sql("""
                        SELECT tenant_id, oidc_issuer, oidc_audience
                          FROM tenant
                         WHERE oidc_issuer = :issuer
                        """)
                .param("issuer", issuer)
                .query((rs, n) -> new IssuerRegistration(TenantId.of(rs.getString("tenant_id")), rs.getString("oidc_issuer"),
                        rs.getString("oidc_audience")))
                .list();
        if (rows.size() > 1) {
            throw new IllegalStateException("tenant.oidc_issuer is not unique"); // uq_tenant_oidc_issuer가 막는다
        }
        return rows.stream().findFirst();
    }

    /**
     * 요청 출처 정확 일치 하나(UNIQUE, 포털 Phase 8 계획 Q2 조건 A) → 그 테넌트의 발급자·공개 클라이언트 id. 테넌트·audience는 싣지 않는다(화면에 줄 값만).
     * 없으면 빈 값 — 등록되지 않은 출처, 또는 로그인 설정이 없는 테넌트.
     */
    public Optional<WebLogin> byWebOrigin(String origin) {
        Objects.requireNonNull(origin, "origin");
        List<WebLogin> rows = client.sql("""
                        SELECT oidc_issuer, oidc_client_id
                          FROM tenant
                         WHERE web_origin = :origin
                        """)
                .param("origin", origin)
                .query((rs, n) -> new WebLogin(rs.getString("oidc_issuer"), rs.getString("oidc_client_id")))
                .list();
        if (rows.size() > 1) {
            throw new IllegalStateException("tenant.web_origin is not unique"); // uq_tenant_web_origin이 막는다
        }
        return rows.stream().findFirst();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

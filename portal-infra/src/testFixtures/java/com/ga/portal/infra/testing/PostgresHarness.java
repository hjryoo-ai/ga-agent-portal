package com.ga.portal.infra.testing;

import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * 통합 테스트용 PostgreSQL 18 하네스. JVM당 1회 기동·재사용(싱글턴).
 *
 * <ol>
 *   <li>이미지는 버전 카탈로그의 {@code postgres-image}(digest 고정) — 한 곳에서만 정한다.</li>
 *   <li>{@code POSTGRES_DB=portal}로 띄우고 {@code docker/postgres/init-roles.sql}을 초기화 스크립트로 실행한다.</li>
 *   <li>{@code portal_migrator}로 Flyway 마이그레이션(V1~).</li>
 *   <li>테스트 데이터소스는 {@code portal_app}(RLS 대상). 시드는 {@code portal_migrator}(FORCE RLS라 테넌트 설정 필요).</li>
 * </ol>
 * Docker가 없으면 {@link #get()}이 예외를 던져 테스트가 <b>실패</b>한다(스킵하지 않는다).
 */
public final class PostgresHarness {

    public static final String DATABASE = "portal";
    public static final String MIGRATOR = "portal_migrator";
    public static final String APP = "portal_app";
    public static final String DIRECTORY = "portal_directory";
    public static final String ISSUER = "portal_issuer";
    // init-roles.sql과 짝을 이루는 로컬 전용 자격 증명
    public static final String MIGRATOR_PASSWORD = "migrator_local_only";
    public static final String APP_PASSWORD = "app_local_only";
    public static final String DIRECTORY_PASSWORD = "directory_local_only";
    public static final String ISSUER_PASSWORD = "issuer_local_only";

    private static final Pattern IMAGE_LINE = Pattern.compile("(?m)^postgres-image\\s*=\\s*\"([^\"]+)\"");

    private static PostgresHarness instance;

    private final PostgreSQLContainer container;
    private final DataSource app;
    private final DataSource migrator;
    private final DataSource superuser;
    private final DataSource directory;
    private final DataSource issuer;

    private PostgresHarness() {
        Path initRoles = repoRoot().resolve("docker/postgres/init-roles.sql");
        if (!Files.isRegularFile(initRoles)) {
            throw new IllegalStateException("init-roles.sql not found: " + initRoles);
        }
        container = new PostgreSQLContainer(DockerImageName.parse(image()).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName(DATABASE)
                .withUsername("postgres")
                .withPassword("postgres")
                .withCopyFileToContainer(MountableFile.forHostPath(initRoles, 0644), "/docker-entrypoint-initdb.d/00-init-roles.sql");
        container.start();

        superuser = dataSource("postgres", "postgres");
        migrator = dataSource(MIGRATOR, MIGRATOR_PASSWORD);
        app = dataSource(APP, APP_PASSWORD);
        directory = dataSource(DIRECTORY, DIRECTORY_PASSWORD);
        issuer = dataSource(ISSUER, ISSUER_PASSWORD);

        Flyway.configure()
                .dataSource(migrator)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public static synchronized PostgresHarness get() {
        if (instance == null) {
            instance = new PostgresHarness();
        }
        return instance;
    }

    /** 버전 카탈로그의 {@code postgres-image}(digest 고정 참조). */
    public static String image() {
        try {
            Matcher m = IMAGE_LINE.matcher(Files.readString(repoRoot().resolve("gradle/libs.versions.toml")));
            if (!m.find() || !m.group(1).contains("@sha256:")) {
                throw new IllegalStateException("gradle/libs.versions.toml has no digest-pinned postgres-image");
            }
            return m.group(1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DataSource appDataSource() {
        return app;
    }

    public DataSource migratorDataSource() {
        return migrator;
    }

    /** {@code portal_directory}(테넌트 목록 전용 — V3). */
    public DataSource directoryDataSource() {
        return directory;
    }

    /** {@code portal_issuer}(발급자 표 전용 — V5). */
    public DataSource issuerDataSource() {
        return issuer;
    }

    public DataSource superuserDataSource() {
        return superuser;
    }

    public String jdbcUrl() {
        return container.getJdbcUrl();
    }

    /** {@code portal_migrator}로 한 트랜잭션을 열고 {@code app.tenant_id}를 설정한 뒤 실행·커밋한다(시드용). */
    public void seed(String tenantId, SqlWork work) {
        inTransaction(migrator, tenantId, c -> {
            work.run(c);
            return null;
        }, true);
    }

    /** {@code portal_migrator}로 실행 후 커밋한다(테넌트 설정 없음 — 카탈로그 조회용). */
    public <T> T asMigrator(SqlFunction<T> fn) {
        return inTransaction(migrator, null, fn, true);
    }

    /** {@code portal_app}으로 한 트랜잭션을 열고(테넌트가 {@code null}이면 설정하지 않음) 실행 후 롤백한다. */
    public <T> T asApp(String tenantIdOrNull, SqlFunction<T> fn) {
        return inTransaction(app, tenantIdOrNull, fn, false);
    }

    private static <T> T inTransaction(DataSource ds, String tenantId, SqlFunction<T> fn, boolean commit) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (tenantId != null) {
                    setTenant(c, tenantId);
                }
                T result = fn.apply(c);
                if (commit) {
                    c.commit();
                } else {
                    c.rollback();
                }
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public static void setTenant(Connection c, String tenantId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId);
            ps.execute();
        }
    }

    private DataSource dataSource(String user, String password) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(container.getJdbcUrl());
        ds.setDatabaseName(DATABASE);
        ds.setUser(user);
        ds.setPassword(password);
        return ds;
    }

    private static Path repoRoot() {
        String root = System.getProperty("ga.repoRoot");
        if (root == null) {
            throw new IllegalStateException("system property ga.repoRoot is not set (configured by the Gradle build)");
        }
        return Path.of(root);
    }

    @FunctionalInterface
    public interface SqlWork {
        void run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    /** SQL 예외를 SQLSTATE와 함께 전달한다. */
    public static final class UncheckedSqlException extends RuntimeException {
        public UncheckedSqlException(SQLException cause) {
            super(cause.getSQLState() + ": " + cause.getMessage(), cause);
        }

        public String sqlState() {
            return ((SQLException) getCause()).getSQLState();
        }
    }
}

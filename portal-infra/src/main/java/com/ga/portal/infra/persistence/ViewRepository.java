package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * 뷰 저장소(Phase 2)의 공통 기반 — 열 읽기·쓰기: 시각은 UTC {@link OffsetDateTime}로 쓰고 {@link Instant}로 읽는다, NULL은 {@link Optional}.
 * {@link TenantScopedRepository} 하위라 결과 타입({@code ResultSet})을 쓸 수 있다(Phase 0 수용 심사 DE-5 — 결과 수준 JDBC는 저장소 안에서만).
 */
abstract class ViewRepository extends TenantScopedRepository {

    ViewRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    protected static Optional<Long> optLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? Optional.empty() : Optional.of(v);
    }

    protected static Optional<String> optString(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getString(column));
    }

    protected static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    protected static Optional<Instant> optInstant(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getObject(column, OffsetDateTime.class)).map(OffsetDateTime::toInstant);
    }

    protected static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    protected static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    protected static Object utc(Optional<Instant> at) {
        return at.map(ViewRepository::utc).orElse(null);
    }

    protected static Object orNull(Optional<?> v) {
        return v.orElse(null);
    }
}

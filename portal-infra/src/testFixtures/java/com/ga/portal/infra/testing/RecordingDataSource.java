package com.ga.portal.infra.testing;

import org.springframework.jdbc.datasource.DataSourceUtils;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * 실제 데이터소스를 감싸 커넥션 획득 수와 준비·실행한 SQL 문장을 순서대로 기록한다(C1·C4 — "DB 도달 전", "첫 문장이 테넌트 설정").
 * 기록하는 것은 문장 텍스트뿐이다(바인드 값은 기록하지 않는다).
 */
public final class RecordingDataSource implements DataSource {

    private final DataSource delegate;
    private final AtomicInteger connections = new AtomicInteger();
    private final List<String> statements = new ArrayList<>();

    public RecordingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    public int connectionsObtained() {
        return connections.get();
    }

    public synchronized List<String> statements() {
        return List.copyOf(statements);
    }

    /** 지금 스레드의 트랜잭션에 묶인 커넥션으로 단일 값 조회를 실행한다(기록된다). */
    public String queryOnBoundConnection(String sql) {
        Connection c = DataSourceUtils.getConnection(this);
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new PostgresHarness.UncheckedSqlException(e);
        } finally {
            DataSourceUtils.releaseConnection(c, this);
        }
    }

    private synchronized void record(String sql) {
        statements.add(sql);
    }

    @Override
    public Connection getConnection() throws SQLException {
        connections.incrementAndGet();
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        connections.incrementAndGet();
        return wrap(delegate.getConnection(username, password));
    }

    private Connection wrap(Connection target) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ((name.equals("prepareStatement") || name.equals("prepareCall")) && args != null && args[0] instanceof String sql) {
                record(sql);
            }
            Object result = invoke(target, method, args);
            if (name.equals("createStatement") && result instanceof Statement statement) {
                return wrapStatement(statement);
            }
            return result;
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private Statement wrapStatement(Statement target) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().startsWith("execute") && args != null && args.length > 0 && args[0] instanceof String sql) {
                record(sql);
            }
            return invoke(target, method, args);
        };
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, handler);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}

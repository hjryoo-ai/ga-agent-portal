package com.ga.portal.infra.persistence;

import com.ga.platform.core.tenant.TenantNotBoundException;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.RecordingDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.Clock;
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
    private final IngestCursorRepository cursors = new IngestCursorRepository(new TenantJdbcGateway(recording), Clock.systemUTC());

    /** 저장소에 공개 메서드가 늘면 이 목록과 아래 호출을 함께 늘린다(새 메서드가 시험 밖에 남지 않게). */
    @Test
    void theGuardedMethodsAreAllPublicMethodsOfBothRepositories() {
        assertThat(publicMethods(TenantRepository.class)).containsExactly("current");
        assertThat(publicMethods(IngestCursorRepository.class)).containsExactly("find", "save");
    }

    @Test
    void everyRepositoryMethodFailsBeforeReachingTheDatabaseWhenUnbound() {
        assertThatThrownBy(tenants::current).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(cursors::find).isInstanceOf(TenantNotBoundException.class);
        assertThatThrownBy(() -> cursors.save(1, Optional.empty(), IngestState.RUNNING, Optional.empty()))
                .isInstanceOf(TenantNotBoundException.class);
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

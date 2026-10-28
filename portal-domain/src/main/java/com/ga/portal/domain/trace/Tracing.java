package com.ga.portal.domain.trace;

import com.ga.platform.core.tenant.TenantId;

import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 트레이스 스팬 포트(포털 Phase 7 지시문 ③, 계획 Q6 — {@code docs/지표.md} §4). 구현은 {@code portal-app}(Micrometer Tracing → OpenTelemetry, 프로세스 안).
 * 스팬은 시작한 스레드에서 현재 스팬이 되고 닫힐 때까지 그 안에서 시작한 스팬의 부모다.
 * <p>속성은 세 꼴뿐이다 — 테넌트, 수({@code long}), 종류(열거 이름 꼴 {@code [A-Z][A-Za-z_]*}). 종류 꼴이 아닌 문자열(설계사 ID·{@code ym}·증권번호·본문)은
 * {@link #OTHER}로 바뀐다 — 값이 속성으로 새는 길이 없다.
 */
public interface Tracing {

    String OTHER = "OTHER";

    Span start(String name);

    /** 이름의 스팬 안에서 {@code body}. */
    default <T> T in(String name, Supplier<T> body) {
        Span span = start(name);
        try {
            return body.get();
        } finally {
            span.close();
        }
    }

    default void run(String name, Runnable body) {
        in(name, () -> {
            body.run();
            return null;
        });
    }

    /** 종류 값 — 열거 이름 꼴이 아니면 {@link #OTHER}. */
    static String kind(String value) {
        return value != null && Kinds.PATTERN.matcher(value).matches() ? value : OTHER;
    }

    /** 열린 스팬. {@link #close()}가 끝내고 현재 스팬을 부모로 되돌린다. */
    interface Span extends AutoCloseable {

        Span tenant(TenantId tenant);

        Span count(String key, long value);

        /** 값은 {@link Tracing#kind(String)}를 거친다. */
        Span kind(String key, String value);

        default Span kind(String key, Enum<?> value) {
            return kind(key, value.name());
        }

        @Override
        void close();
    }

    Tracing NONE = name -> Kinds.NOOP;

    /** 내부 상수. */
    final class Kinds {
        static final Pattern PATTERN = Pattern.compile("[A-Z][A-Za-z_]{0,63}");

        static final Span NOOP = new Span() {
            @Override
            public Span tenant(TenantId tenant) {
                return this;
            }

            @Override
            public Span count(String key, long value) {
                return this;
            }

            @Override
            public Span kind(String key, String value) {
                return this;
            }

            @Override
            public void close() {
            }
        };

        private Kinds() {
        }
    }
}

package com.ga.portal.demo;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 재생 속도(지시문 ②ⓔ — 화면의 동기화 칩이 움직이는 것을 보이려고). 보이는 이벤트 수만 정한다: {@code instant}는 처음부터 전부,
 * {@code paced:<n>:<ms>}는 0에서 시작해 {@code ms}마다 {@code n}건씩 더한다(덧셈만 — 데모 모듈도 곱셈 스캔 대상이다).
 */
public final class Replay {

    private final int total;
    private final int step;
    private final long intervalMillis;
    private final AtomicInteger visible;

    private Replay(int total, int step, long intervalMillis, int start) {
        this.total = total;
        this.step = step;
        this.intervalMillis = intervalMillis;
        this.visible = new AtomicInteger(start);
    }

    public static Replay instant(int total) {
        return new Replay(total, 0, 0, total);
    }

    public static Replay paced(int total, int step, long intervalMillis) {
        if (step < 1 || intervalMillis < 1) {
            throw new IllegalArgumentException("paced replay needs a positive step and interval");
        }
        return new Replay(total, step, intervalMillis, 0);
    }

    /** {@code instant} 또는 {@code paced:<n>:<ms>}. */
    public static Replay parse(String spec, int total) {
        Objects.requireNonNull(spec, "spec");
        if (spec.equals("instant")) {
            return instant(total);
        }
        String[] parts = spec.split(":", -1);
        if (parts.length == 3 && parts[0].equals("paced")) {
            return paced(total, Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
        }
        throw new IllegalArgumentException("replay is instant or paced:<events>:<millis>");
    }

    /** 지금 보이는 이벤트 수(앞에서부터). */
    public int visible() {
        return visible.get();
    }

    /** 한 틱 — 보이는 수에 {@code n}을 더한다(전체를 넘지 않는다). {@code instant}면 아무것도 하지 않는다. */
    public void tick() {
        visible.updateAndGet(v -> Math.min(total, v + step));
    }

    public boolean paced() {
        return step > 0;
    }

    public long intervalMillis() {
        return intervalMillis;
    }
}

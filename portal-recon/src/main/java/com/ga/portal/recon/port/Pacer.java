package com.ga.portal.recon.port;

import java.time.Duration;

/** 엔진 호출 사이 간격({@code recon_call_interval_ms}) — 시험은 기다리지 않는다. */
@FunctionalInterface
public interface Pacer {

    void pause(Duration interval);
}

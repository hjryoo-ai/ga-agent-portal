package com.ga.portal.app.web;

import com.ga.portal.sim.SimRequests;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 시뮬레이션 본문은 상한+1바이트까지만 읽는다(포털 Phase 5 — 상한을 넘는 본문을 다 받아 놓고 거부하지 않는다). */
class SimulationControllerTest {

    /** 끝없는 본문: 읽은 바이트 수를 센다. */
    static final class Endless extends InputStream {
        long read;

        @Override
        public int read() {
            read++;
            return '{';
        }

        @Override
        public int read(byte[] b, int off, int len) {
            java.util.Arrays.fill(b, off, off + len, (byte) '{');
            read += len;
            return len;
        }
    }

    @Test
    void anEndlessBodyIsReadOnlyToTheCapPlusOne() throws IOException {
        Endless in = new Endless();
        assertThat(SimulationController.bounded(in)).hasSize(SimRequests.MAX_BODY_BYTES + 1);
        assertThat(in.read).isEqualTo(SimRequests.MAX_BODY_BYTES + 1L);
    }

    @Test
    void aBodyWithinTheCapIsReadWhole() throws IOException {
        byte[] body = "{\"productKey\":\"WHOLE-LIFE-20Y\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(SimulationController.bounded(new ByteArrayInputStream(body))).isEqualTo(body);
        assertThat(SimulationController.bounded(InputStream.nullInputStream())).isEmpty();
    }
}

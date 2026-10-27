package com.ga.portal.app.recon;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 대사 스케줄러(계획 Q9): {@code portal.recon.tick}(기본 1분) 고정 지연 틱 → 테넌트마다 "KST 시각 ≥ {@code recon_hour_kst}이고 오늘 스케줄 회차가 없음"이면
 * 한 회차. {@code portal.recon.scheduler-enabled=false}면 조립하지 않는다(통합 시험·E2E·CLI — P-11).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "portal.recon", name = "scheduler-enabled", havingValue = "true")
@EnableScheduling
public class ReconSchedulingConfiguration {

    @Bean
    Ticker reconTicker(ReconService service) {
        return new Ticker(service);
    }

    static final class Ticker {
        private final ReconService service;

        Ticker(ReconService service) {
            this.service = service;
        }

        @Scheduled(fixedDelayString = "${portal.recon.tick}", initialDelayString = "${portal.recon.tick}")
        void tick() {
            service.tick();
        }
    }
}

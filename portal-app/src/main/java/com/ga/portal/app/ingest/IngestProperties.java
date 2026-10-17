package com.ga.portal.app.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 인제스트 설정({@code portal.ingest.*}). 폴링은 {@code enabled=true}일 때만 돈다 — 기본은 꺼짐(배포 구성이 켠다, 포털 Phase 8).
 *
 * @param tokenDir          테넌트별 서비스 토큰 파일 디렉터리(계획 승인 Q7)
 * @param directoryUsername 테넌트 목록 전용 롤(V3 {@code portal_directory})
 * @param rebuildMaxEvents  재구축 상한(Phase 2 계획 ⑥) — 피드 {@code headSeq}가 이보다 크면 시작하지 않는다(txB 길이를 묶는다)
 */
@ConfigurationProperties("portal.ingest")
public record IngestProperties(boolean enabled, int limit, Duration defaultPollInterval, Duration httpTimeout, Duration tick, Path tokenDir,
                               String directoryUsername, String directoryPassword, long rebuildMaxEvents) {
}

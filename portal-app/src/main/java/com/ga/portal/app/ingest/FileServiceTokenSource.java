package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테넌트별 엔진 서비스 토큰 파일(계획 승인 Q7): {@code <token-dir>/<TENANT_ID>} 한 줄. 없거나 비면 비어 있다(→ {@code PAUSED(TOKEN_MISSING)}).
 * 소유자 밖에서 읽을 수 있으면 처음 한 번 경고한다(차단하지 않는다 — 컨테이너 마운트마다 다르다). 값은 로그에 쓰지 않는다.
 */
final class FileServiceTokenSource implements ServiceTokenSource {

    private static final Logger LOG = LoggerFactory.getLogger(FileServiceTokenSource.class);

    private final Path dir;
    private final Set<TenantId> warned = ConcurrentHashMap.newKeySet();

    FileServiceTokenSource(Path dir) {
        this.dir = dir;
    }

    @Override
    public Optional<ServiceToken> tokenFor(TenantId tenant) {
        Path file = dir.resolve(tenant.value());
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        warnIfReadableByOthers(tenant, file);
        try {
            String value = Files.readString(file, StandardCharsets.UTF_8).strip();
            return value.isEmpty() ? Optional.empty() : Optional.of(new ServiceToken(value));
        } catch (IOException e) {
            LOG.warn("service token file of tenant {} is unreadable: {}", tenant.value(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void warnIfReadableByOthers(TenantId tenant, Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            if ((perms.contains(PosixFilePermission.GROUP_READ) || perms.contains(PosixFilePermission.OTHERS_READ)) && warned.add(tenant)) {
                LOG.warn("service token file of tenant {} is readable by group/others ({}) — restrict it to the owner", tenant.value(), perms);
            }
        } catch (UnsupportedOperationException | IOException e) {
            // POSIX 권한이 없는 파일 시스템 — 검사하지 않는다
        }
    }
}

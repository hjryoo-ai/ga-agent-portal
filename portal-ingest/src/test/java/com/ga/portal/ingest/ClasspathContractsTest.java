package com.ga.portal.ingest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 수신 검증이 쓰는 클래스패스 계약 사본 = 저장소 {@code contracts/}의 바이트(계획 ⑥, 승인 — 원격 조회 0). */
class ClasspathContractsTest {

    private static final Path CONTRACTS = Path.of(System.getProperty("ga.repoRoot"), "contracts");

    @Test
    void everyCopiedContractEqualsTheRepositoryBytes() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(CONTRACTS)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String rel = CONTRACTS.relativize(p).toString().replace('\\', '/');
                        return rel.startsWith("events/v1/") || rel.startsWith("api/v1/") || rel.startsWith("shared/ga-disclosure/events/v1/");
                    })
                    .toList();
        }
        assertThat(files).hasSizeGreaterThanOrEqualTo(1 + 2 + 13 + (1 + 6) + 1);   // 샘플: A-1 스트림 파일 하나 + 단건 6(계약 v1.4)
        for (Path file : files) {
            String rel = CONTRACTS.relativize(file).toString().replace('\\', '/');
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("contracts/" + rel)) {
                assertThat(in).as("classpath copy of %s", rel).isNotNull();
                assertThat(in.readAllBytes()).as(rel).isEqualTo(Files.readAllBytes(file));
            }
        }
    }

    @Test
    void typeKindPairsAndMaxVersionComeFromTheEnvelopeSchema() {
        ContractSchemas s = ContractSchemas.get();
        assertThat(s.maxVersion()).isEqualTo(1);
        assertThat(s.kindByType()).hasSize(13)
                .containsEntry("CommissionCalculated", "COMM_CALC")
                .containsEntry("StatementConfirmed", "STATEMENT")
                .containsEntry("PayoutCompleted", "PAYOUT")
                .containsEntry("LimitLedgerRebased", "LIMIT_LEDGER");
    }

    @Test
    void schemaIrisOutsideTheRepositoryAreRefused() {
        assertThatThrownBy(() -> ContractSchemas.read("events/v1/none.json")).hasMessageContaining("not on the classpath");
        assertThat(ContractSchemas.get().feedResponse()).isNotNull();
    }
}

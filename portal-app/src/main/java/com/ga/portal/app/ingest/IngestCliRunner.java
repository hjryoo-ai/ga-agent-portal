package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

/**
 * {@code cli} 프로파일의 진입: {@code ingest status [--tenant=T]}, {@code ingest resume --tenant=T --from-seq=n},
 * {@code ingest pause --tenant=T}, {@code ingest rebuild --tenant=T}. 행위자는 OS 사용자({@code user.name}) 하나 — {@code cli:<user>}가 그 테넌트의
 * {@code OPERATOR} 링크여야 한다(Phase 3 D-9). {@code --actor}는 폐기(사용법 오류 64 — 인자로 남을 사칭할 길을 두지 않는다). OS 사용자가 없거나 형식 밖이면 거부(2).
 * 결과는 표준 출력과 종료 코드(0 성공, 1 재구축 실패, 2 거부, 64 사용법).
 */
public final class IngestCliRunner implements ApplicationRunner, ExitCodeGenerator {

    static final String USAGE = "usage: ingest status [--tenant=T] | ingest resume --tenant=T --from-seq=n | ingest pause --tenant=T | ingest rebuild --tenant=T"
            + "  (the actor is the OS user, registered as an OPERATOR identity_link per tenant)";
    static final String ACTOR_REMOVED = "--actor is not supported: the OS user is the actor (Phase 3 — register cli:<user> as an OPERATOR identity_link)";

    private final IngestCommands commands;
    private final PrintStream out;
    private final String osUser;
    private int exitCode;

    public IngestCliRunner(IngestCommands commands, PrintStream out, String osUser) {
        this.commands = commands;
        this.out = out;
        this.osUser = osUser;
    }

    @Override
    public void run(ApplicationArguments args) {
        IngestCommands.Outcome outcome = execute(args.getNonOptionArgs(), one(args, "tenant"), one(args, "from-seq"), one(args, "actor"));
        outcome.lines().forEach(out::println);
        exitCode = outcome.exitCode();
    }

    IngestCommands.Outcome execute(List<String> words, Optional<String> tenant, Optional<String> fromSeq, Optional<String> actor) {
        if (words.size() != 2 || !words.getFirst().equals("ingest")) {
            return new IngestCommands.Outcome(64, List.of(USAGE));
        }
        if (actor.isPresent()) {
            return new IngestCommands.Outcome(64, List.of(USAGE, ACTOR_REMOVED));
        }
        Optional<String> subject = IngestCommands.actorSubject(osUser);
        if (subject.isEmpty()) {
            return new IngestCommands.Outcome(2, List.of("no valid OS user to act as ([A-Za-z0-9._@-], up to 64)"));
        }
        try {
            return switch (words.get(1)) {
                case "status" -> commands.status(tenant.map(TenantId::of), subject.get());
                case "resume" -> commands.resume(TenantId.of(tenant.orElseThrow(() -> new IllegalArgumentException("--tenant"))),
                        Long.parseLong(fromSeq.orElseThrow(() -> new IllegalArgumentException("--from-seq"))), subject.get());
                case "pause" -> commands.pause(TenantId.of(tenant.orElseThrow(() -> new IllegalArgumentException("--tenant"))), subject.get());
                case "rebuild" -> commands.rebuild(TenantId.of(tenant.orElseThrow(() -> new IllegalArgumentException("--tenant"))), subject.get());
                default -> new IngestCommands.Outcome(64, List.of(USAGE));
            };
        } catch (IllegalArgumentException e) {
            return new IngestCommands.Outcome(64, List.of(USAGE, "invalid or missing " + e.getMessage()));
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private static Optional<String> one(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getLast());
    }
}

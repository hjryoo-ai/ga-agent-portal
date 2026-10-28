package com.ga.portal.app.cli;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.app.recon.ReconCommands;
import com.ga.portal.domain.InquiryStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@code cli} 프로파일의 <b>유일한</b> 실행기(포털 Phase 6 계획 Q28 — 실행기가 둘이면 Spring이 둘 다 돌린다): 두 묶음.
 * <ul>
 *   <li>{@code ingest status [--tenant=T]}, {@code ingest resume --tenant=T --from-seq=n}, {@code ingest pause --tenant=T}, {@code ingest rebuild --tenant=T}</li>
 *   <li>{@code inquiry list --tenant=T [--status=OPEN|ANSWERED|CLOSED|ALL]}, {@code inquiry answer --tenant=T --id=<uuid> --answer-file=<path>},
 *       {@code inquiry close --tenant=T --id=<uuid>} — 답변은 파일로만({@code --answer}는 없다, 모르는 옵션은 64). 파일의 요건은 {@link AnswerFile}.</li>
 *   <li>{@code recon run --tenant=T [--ym=YYYYMM,…]}(최대 6개), {@code status --tenant=T|--all}(한 단어 명령 — 포털 Phase 7, {@link ReconCommands})</li>
 * </ul>
 * 행위자는 OS 사용자({@code user.name}) 하나 — {@code cli:<user>}가 그 테넌트의 {@code OPERATOR} 링크여야 한다(Phase 3 D-9). {@code --actor}는 폐기(사용법 오류 64 —
 * 인자로 남을 사칭할 길을 두지 않는다). OS 사용자가 없거나 형식 밖이면 거부(2). 결과는 표준 출력과 종료 코드(0 성공, 1 재구축 실패, 2 거부, 64 사용법).
 */
public final class CliRunner implements ApplicationRunner, ExitCodeGenerator {

    static final String USAGE = "usage: ingest status [--tenant=T] | ingest resume --tenant=T --from-seq=n | ingest pause --tenant=T | ingest rebuild --tenant=T"
            + " | inquiry list --tenant=T [--status=OPEN|ANSWERED|CLOSED|ALL] | inquiry answer --tenant=T --id=<uuid> --answer-file=<path>"
            + " | inquiry close --tenant=T --id=<uuid> | recon run --tenant=T [--ym=YYYYMM,...] | status --tenant=T|--all"
            + "  (the actor is the OS user, registered as an OPERATOR identity_link per tenant)";
    static final String ACTOR_REMOVED = "--actor is not supported: the OS user is the actor (Phase 3 — register cli:<user> as an OPERATOR identity_link)";
    static final Set<String> INQUIRY_OPTIONS = Set.of("tenant", "status", "id", "answer-file");
    static final Set<String> RECON_OPTIONS = Set.of("tenant", "ym");
    static final Set<String> STATUS_OPTIONS = Set.of("tenant", "all");
    private static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final IngestCommands ingest;
    private final InquiryCommands inquiry;
    private final ReconCommands recon;
    private final PrintStream out;
    private final String osUser;
    private int exitCode;

    public CliRunner(IngestCommands ingest, InquiryCommands inquiry, ReconCommands recon, PrintStream out, String osUser) {
        this.ingest = ingest;
        this.inquiry = inquiry;
        this.recon = recon;
        this.out = out;
        this.osUser = osUser;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String name : args.getOptionNames()) {
            if (name.contains(".")) {
                continue;   // Spring 속성(--spring.datasource.url=… 등 — 시험·운영이 인자로 줄 수 있다). 명령 옵션 이름에는 점이 없다
            }
            List<String> values = args.getOptionValues(name);
            options.put(name, values == null || values.isEmpty() ? "" : values.getLast());
        }
        IngestCommands.Outcome outcome = execute(args.getNonOptionArgs(), options);
        outcome.lines().forEach(out::println);
        exitCode = outcome.exitCode();
    }

    IngestCommands.Outcome execute(List<String> words, Map<String, String> options) {
        boolean status = words.equals(List.of("status"));
        if (!status && (words.size() != 2 || !Set.of("ingest", "inquiry", "recon").contains(words.getFirst()))) {
            return usage();
        }
        if (options.containsKey("actor")) {
            return new IngestCommands.Outcome(64, List.of(USAGE, ACTOR_REMOVED));
        }
        Set<String> allowed = status ? STATUS_OPTIONS : switch (words.getFirst()) {
            case "inquiry" -> INQUIRY_OPTIONS;
            case "recon" -> RECON_OPTIONS;
            default -> null;   // ingest: 옵션 검사 없음(Phase 1 그대로)
        };
        if (allowed != null) {
            Optional<String> unknown = options.keySet().stream().filter(k -> !allowed.contains(k)).findFirst();
            if (unknown.isPresent()) {
                return new IngestCommands.Outcome(64, List.of(USAGE, "unknown option --" + unknown.get()));
            }
        }
        Optional<String> subject = IngestCommands.actorSubject(osUser);
        if (subject.isEmpty()) {
            return new IngestCommands.Outcome(2, List.of("no valid OS user to act as ([A-Za-z0-9._@-], up to 64)"));
        }
        try {
            if (status) {
                return status(options, subject.get());
            }
            return switch (words.getFirst()) {
                case "ingest" -> ingest(words.get(1), options, subject.get());
                case "recon" -> words.get(1).equals("run")
                        ? recon.run(tenant(options), ReconCommands.months(Optional.ofNullable(options.get("ym"))), subject.get()) : usage();
                default -> inquiry(words.get(1), options, subject.get());
            };
        } catch (IllegalArgumentException e) {
            return new IngestCommands.Outcome(64, List.of(USAGE, "invalid or missing " + e.getMessage()));
        } catch (AnswerFile.Invalid e) {
            return new IngestCommands.Outcome(64, List.of(USAGE, e.getMessage()));
        }
    }

    private IngestCommands.Outcome ingest(String verb, Map<String, String> options, String subject) {
        Optional<String> tenant = Optional.ofNullable(options.get("tenant"));
        return switch (verb) {
            case "status" -> ingest.status(tenant.map(TenantId::of), subject);
            case "resume" -> ingest.resume(tenant(options), Long.parseLong(required(options, "from-seq")), subject);
            case "pause" -> ingest.pause(tenant(options), subject);
            case "rebuild" -> ingest.rebuild(tenant(options), subject);
            default -> usage();
        };
    }

    private IngestCommands.Outcome inquiry(String verb, Map<String, String> options, String subject) throws AnswerFile.Invalid {
        return switch (verb) {
            case "list" -> inquiry.list(tenant(options), status(options.getOrDefault("status", "ALL")), subject);
            case "answer" -> {
                TenantId t = tenant(options);
                UUID id = id(options);
                String answer = AnswerFile.read(Path.of(required(options, "answer-file")));
                yield inquiry.answer(t, id, answer, subject);
            }
            case "close" -> inquiry.close(tenant(options), id(options), subject);
            default -> usage();
        };
    }

    /** {@code status}: {@code --tenant=T}와 {@code --all} 중 정확히 하나({@code --all}은 값 없음). */
    private IngestCommands.Outcome status(Map<String, String> options, String subject) {
        boolean all = options.containsKey("all");
        if (all == options.containsKey("tenant") || (all && !options.get("all").isEmpty())) {
            throw new IllegalArgumentException("--tenant or --all");
        }
        return recon.status(all ? Optional.empty() : Optional.of(tenant(options)), subject);
    }

    private static TenantId tenant(Map<String, String> options) {
        return TenantId.of(required(options, "tenant"));
    }

    private static UUID id(Map<String, String> options) {
        String id = required(options, "id");
        if (!UUID_TEXT.matcher(id).matches()) {
            throw new IllegalArgumentException("--id");
        }
        return UUID.fromString(id);
    }

    private static Optional<InquiryStatus> status(String value) {
        return switch (value) {
            case "ALL" -> Optional.empty();
            case "OPEN" -> Optional.of(InquiryStatus.OPEN);
            case "ANSWERED" -> Optional.of(InquiryStatus.ANSWERED);
            case "CLOSED" -> Optional.of(InquiryStatus.CLOSED);
            default -> throw new IllegalArgumentException("--status");
        };
    }

    private static String required(Map<String, String> options, String name) {
        String v = options.get(name);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("--" + name);
        }
        return v;
    }

    private static IngestCommands.Outcome usage() {
        return new IngestCommands.Outcome(64, List.of(USAGE));
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}

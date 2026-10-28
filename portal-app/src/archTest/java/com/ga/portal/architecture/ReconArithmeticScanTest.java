package com.ga.portal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 C2(지시문 §0 "대사는 세 정수의 동등 비교뿐"): 대사는 금액의 차이·합·부호 반전을 만들지 않는다 — 곱셈·SQL 산술 스캔({@code MultiplicationScanTest}·
 * {@code SqlArithmeticScanTest} — 무변경)은 {@code *}·{@code /}·{@code %}만 보므로, 대사의 뺄셈·덧셈은 여기서 따로 막는다(허용 목록 없음).
 * <ul>
 *   <li>바이트코드: {@code com.ga.portal.recon..}와 대사 저장 어댑터({@code JdbcReconStore})에 {@code long} 산술({@code LADD}·{@code LSUB}·{@code LMUL}·
 *       {@code LDIV}·{@code LREM}·{@code LNEG})과 {@code Math}·{@code StrictMath}의 {@code add·subtract·negate·abs·multiply·…Exact} 호출이 없다. 포털의 금액은
 *       {@code long}이다(대사의 수 — 호출 수·검사 수 — 는 {@code int}).</li>
 *   <li>SQL: {@code recon_result}·{@code recon_run}을 읽거나 쓰는 자바 문장(실행 SQL)에 {@code +}·{@code -}·{@code *}·{@code /}·{@code %}가 없다
 *       ({@code count(*)}·{@code select *} 제외, 주석·문자열은 {@link SqlTenantScanner}가 비운다).</li>
 * </ul>
 */
class ReconArithmeticScanTest {

    static final Set<Opcode> LONG_ARITHMETIC = EnumSet.of(Opcode.LADD, Opcode.LSUB, Opcode.LMUL, Opcode.LDIV, Opcode.LREM, Opcode.LNEG);
    static final Set<String> MATH = Set.of("java/lang/Math", "java/lang/StrictMath");
    static final Pattern MATH_METHOD = Pattern.compile("(add|subtract|negate|multiply|increment|decrement).*|abs.*|floorDiv|floorMod|ceilDiv");
    static final Set<String> ADAPTERS = Set.of("com.ga.portal.app.recon.JdbcReconStore");
    private static final Pattern NOT_ARITHMETIC = Pattern.compile("count\\(\\s*\\*\\s*\\)|\\bselect\\s+(?:distinct\\s+)?\\*|[a-z_][a-z0-9_]*\\.\\*");
    private static final Pattern OPERATOR = Pattern.compile("[-+*/%]");
    private static final Pattern RECON_TABLE = Pattern.compile("\\brecon_(result|run)\\b");
    private static final Path ROOT = Path.of(System.getProperty("ga.repoRoot"));

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter().withImportOption(ArchitectureRulesTest.PRODUCTION_ONLY).importPackages("com.ga.portal");
    }

    static List<String> sites(byte[] classBytes) {
        ClassModel model = ClassFile.of().parse(classBytes);
        String owner = model.thisClass().asInternalName().replace('/', '.');
        List<String> out = new ArrayList<>();
        for (MethodModel m : model.methods()) {
            m.code().ifPresent(code -> {
                for (CodeElement e : code) {
                    if (e instanceof OperatorInstruction op && LONG_ARITHMETIC.contains(op.opcode())) {
                        out.add(owner + "#" + m.methodName().stringValue() + " " + op.opcode().name());
                    } else if (e instanceof InvokeInstruction call && MATH.contains(call.owner().asInternalName())
                            && MATH_METHOD.matcher(call.name().stringValue()).matches()) {
                        out.add(owner + "#" + m.methodName().stringValue() + " Math." + call.name().stringValue());
                    }
                }
            });
        }
        return out;
    }

    @Test
    void theReconcilerHasNoLongArithmetic() {
        List<JavaClass> recon = classes.stream()
                .filter(c -> c.getPackageName().startsWith("com.ga.portal.recon") || ADAPTERS.contains(c.getName().replaceFirst("\\$.*$", "")))
                .toList();
        assertThat(recon).as("recon classes scanned (not vacuous)").hasSizeGreaterThan(20)
                .anyMatch(c -> c.getName().equals("com.ga.portal.recon.rule.ReconRules"))
                .anyMatch(c -> c.getName().equals("com.ga.portal.app.recon.JdbcReconStore"));
        List<String> found = new ArrayList<>();
        recon.forEach(c -> found.addAll(sites(MultiplicationScanTest.bytesOf(c))));
        assertThat(found).as("long arithmetic in the reconciler").isEmpty();
    }

    @Test
    void reconSqlHasNoArithmetic() throws IOException {
        List<String> statements = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        for (String module : TenantPredicateScanTest.MODULES) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(module).resolve("src").resolve("main"))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    for (String literal : SqlTenantScanner.sqlLiteralsInJava(Files.readString(p, StandardCharsets.UTF_8))) {
                        for (String statement : SqlTenantScanner.statementsOf(literal)) {
                            if (!RECON_TABLE.matcher(statement).find()) {
                                continue;
                            }
                            statements.add(statement);
                            String blanked = NOT_ARITHMETIC.matcher(statement).replaceAll(m -> " ".repeat(m.group().length()));
                            Matcher op = OPERATOR.matcher(blanked);
                            while (op.find()) {
                                hits.add(ROOT.relativize(p) + ": …" + statement.substring(Math.max(0, op.start() - 30), Math.min(statement.length(), op.start() + 30)) + "…");
                            }
                        }
                    }
                }
            }
        }
        assertThat(statements).as("recon statements scanned (not vacuous)").hasSizeGreaterThanOrEqualTo(8);
        assertThat(hits).as("arithmetic in recon SQL").isEmpty();
    }

    @Test
    void theScannerSeesLongArithmetic() throws IOException {
        String name = Probe.class.getName();
        byte[] bytes;
        try (var in = Probe.class.getResourceAsStream(name.substring(name.lastIndexOf('.') + 1) + ".class")) {
            bytes = in.readAllBytes();
        }
        assertThat(sites(bytes)).containsExactly(name + "#diff LSUB", name + "#exact Math.subtractExact", name + "#neg LNEG");
    }

    /** 스캐너 자기 시험용 — 제품 코드가 아니다. */
    static final class Probe {
        static long diff(long a, long b) {
            return a - b;
        }

        static long exact(long a, long b) {
            return Math.subtractExact(a, b);
        }

        static long neg(long a) {
            return -a;
        }

        static boolean same(long a, long b) {
            return a == b;
        }

        static int count(int a) {
            return a + 1;
        }
    }
}

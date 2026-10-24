package com.ga.portal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C3(포털 Phase 5 계획 Q16 — 설계서 §6.4): 포털 제품 코드 전체 바이트코드의 곱셈·나눗셈·나머지 위치는 닫힌 허용 목록의 메서드뿐이다. 포털의 금액은 뷰 레코드에서
 * {@code long}이라 타입으로 "금액 곱셈"을 가려낼 수 없다 — 그래서 연산 전부를 막고 메서드 단위로 연다. 금액을 곱하는 곳은 {@code ForecastScenario#apply}
 * 하나(CLAUDE.md 규칙 1의 예외, 지시문 승인)이고 나머지 둘은 금액을 만들지 않는다.
 * <ul>
 *   <li>보는 것: {@code IMUL}·{@code LMUL}·{@code IDIV}·{@code LDIV}·{@code IREM}·{@code LREM}(부동소수 연산은 아키텍처 규칙이 이미 막는다 — 여기서도 센다)과
 *       {@code Math}·{@code StrictMath}·{@code BigInteger}·{@code BigDecimal}의 곱셈·나눗셈·나머지 메서드 호출.</li>
 *   <li>도구: JDK 25 Class-File API({@code java.lang.classfile}) — 새 의존성 0. 클래스 위치는 ArchUnit 가져오기(제품 클래스만)가 준다.</li>
 *   <li>허용 목록은 FQN#메서드와 사유. 쓰이지 않는 항목이 남으면 실패한다. 상수 식(예: {@code 60 * 1000})은 컴파일러가 접어 바이트코드에 없다.</li>
 * </ul>
 */
class MultiplicationScanTest {

    record Allowed(String method, String reason) {
    }

    static final List<Allowed> ALLOWLIST = List.of(
            new Allowed("com.ga.portal.query.rule.ForecastScenario#apply",
                    "분급 시나리오 floor(expected × p / 100) — 포털이 금액을 곱하는 유일한 자리(설계서 §6.4, 지시문 Phase 5 ④ 승인)"),
            new Allowed("com.ga.portal.domain.rule.LimitStage#of",
                    "누적 × 10000과 한도 × 임계치 bp의 정수 비교(Phase 3 D-13) — 단계 이름만 낸다, 금액을 만들지 않는다"),
            new Allowed("com.ga.portal.infra.persistence.AgentQueryRepository#lines",
                    "라인 목록의 오프셋 page × size — 금액이 아니다"));

    static final Set<Opcode> OPERATORS = EnumSet.of(Opcode.IMUL, Opcode.LMUL, Opcode.IDIV, Opcode.LDIV, Opcode.IREM, Opcode.LREM,
            Opcode.FMUL, Opcode.DMUL, Opcode.FDIV, Opcode.DDIV, Opcode.FREM, Opcode.DREM);
    static final Set<String> ARITHMETIC_OWNERS = Set.of("java/lang/Math", "java/lang/StrictMath", "java/math/BigInteger", "java/math/BigDecimal");
    static final Pattern ARITHMETIC_METHOD = Pattern.compile("multiply.*|.*[Dd]iv.*|.*[Mm]od|remainder.*|divide.*");

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter().withImportOption(ArchitectureRulesTest.PRODUCTION_ONLY).importPackages("com.ga.portal");
    }

    /** 메서드(FQN#이름) → 그 안의 곱셈·나눗셈 위치 설명(연산 이름). 람다 본문은 합성 메서드 이름으로 따로 센다. */
    static Map<String, List<String>> sites(byte[] classBytes) {
        ClassModel model = ClassFile.of().parse(classBytes);
        String owner = model.thisClass().asInternalName().replace('/', '.');
        Map<String, List<String>> out = new TreeMap<>();
        for (MethodModel m : model.methods()) {
            List<String> found = new ArrayList<>();
            m.code().ifPresent(code -> {
                for (CodeElement e : code) {
                    if (e instanceof OperatorInstruction op && OPERATORS.contains(op.opcode())) {
                        found.add(op.opcode().name());
                    } else if (e instanceof InvokeInstruction call && ARITHMETIC_OWNERS.contains(call.owner().asInternalName())
                            && ARITHMETIC_METHOD.matcher(call.name().stringValue()).matches()) {
                        found.add(call.owner().asInternalName().substring(call.owner().asInternalName().lastIndexOf('/') + 1) + "." + call.name().stringValue());
                    }
                }
            });
            if (!found.isEmpty()) {
                out.merge(owner + "#" + m.methodName().stringValue(), found, (a, b) -> {
                    List<String> all = new ArrayList<>(a);
                    all.addAll(b);
                    return all;
                });
            }
        }
        return out;
    }

    static byte[] bytesOf(JavaClass c) {
        try (InputStream in = c.getSource().orElseThrow(() -> new IllegalStateException("no source for " + c.getName())).getUri().toURL().openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, List<String>> scanPortal() {
        Map<String, List<String>> all = new TreeMap<>();
        for (JavaClass c : classes) {
            all.putAll(sites(bytesOf(c)));
        }
        return all;
    }

    @Test
    void multiplicationAndDivisionAppearOnlyInTheAllowlistedMethods() {
        Map<String, List<String>> found = scanPortal();
        Set<String> allowed = ALLOWLIST.stream().map(Allowed::method).collect(Collectors.toSet());
        assertThat(found.keySet().stream().filter(k -> !allowed.contains(k)).toList())
                .as("multiplication/division outside the allowlist (portal sites: %s)", found).isEmpty();
        // 닫힌 목록: 쓰이지 않는 항목(코드가 옮겨졌거나 지워졌다)은 제거한다
        assertThat(ALLOWLIST).allSatisfy(a -> {
            assertThat(a.reason()).as("reason for %s", a.method()).isNotBlank();
            assertThat(found).as("stale allowlist entry %s", a.method()).containsKey(a.method());
        });
        assertThat(classes.size()).as("portal production classes scanned (not vacuous)").isGreaterThan(100);
        // 보고서의 "곱셈 위치 목록"
        assertThat(found).containsOnlyKeys(allowed);
        assertThat(found.get("com.ga.portal.query.rule.ForecastScenario#apply")).containsExactly("Math.multiplyExact", "Math.floorDiv");
    }

    @Test
    void theScannerSeesOperatorsAndMathCalls() throws Exception {
        Map<String, List<String>> own = sites(selfBytes());
        assertThat(own.get(Probe.class.getName() + "#times")).containsExactly("LMUL");
        assertThat(own.get(Probe.class.getName() + "#half")).containsExactly("IDIV", "IREM");
        assertThat(own.get(Probe.class.getName() + "#exact")).containsExactly("Math.multiplyExact", "Math.floorMod");
        assertThat(own).doesNotContainKey(Probe.class.getName() + "#plus");
    }

    private static byte[] selfBytes() throws IOException {
        String name = Probe.class.getName();
        try (InputStream in = Probe.class.getResourceAsStream(name.substring(name.lastIndexOf('.') + 1) + ".class")) {
            return in.readAllBytes();
        }
    }

    /** 스캐너 자체 시험용(시험 소스셋 — 제품 클래스가 아니다). 클래스 파일 이름은 바깥 클래스$Probe. */
    static final class Probe {
        static long times(long a, long b) {
            return a * b;
        }

        static int half(int a) {
            return a / 2 + a % 2;
        }

        static long exact(long a, int b) {
            return Math.multiplyExact(a, b) + Math.floorMod(a, 3L);
        }

        static long plus(long a, long b) {
            return a + b - 1;
        }
    }
}

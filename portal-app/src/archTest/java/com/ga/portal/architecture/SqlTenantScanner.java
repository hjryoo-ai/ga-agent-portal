package com.ga.portal.architecture;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 텍스트에서 테넌트 테이블 접근 문장을 찾아 {@code tenant_id} 조건(또는 컬럼 지정)이 있는지 검사한다(ga-disclosure Phase 0의 스캐너를
 * 옮기고 포털 계획 승인 §4 D1~D3을 반영했다).
 *
 * <p>SQL 파서를 쓰지 않는 보수적 스캐너다.
 * <ul>
 *   <li>주석({@code --}, {@code /* *\/})을 지우고, 문자열 리터럴은 빈 리터럴로 바꾼다(문자열 속 SQL은 동적 SQL이라 스캔 대상이 아니다).</li>
 *   <li>달러 인용 본문({@code $$ … $$}, {@code $tag$ … $tag$})은 꺼내서 재귀적으로 같은 규칙으로 검사한다(함수·DO 블록).</li>
 *   <li>최상위와 본문 모두 {@code ;} 단위로 나눈다.</li>
 * </ul>
 * 규칙(두 방언 — 자바 문자열 SQL은 저장소 바인드 규약까지, 마이그레이션 {@code .sql}은 컬럼·조건만)
 * <ul>
 *   <li>CREATE TABLE t / CREATE INDEX … ON t: {@code tenant_id} 컬럼 포함(구조 검사 — 술어 규칙은 DDL에 적용하지 않는다, D1).</li>
 *   <li>INSERT INTO t (…): 컬럼 목록에 {@code tenant_id}. 자바 SQL은 그 자리의 값이 {@code :tenantId}여야 한다(D3).</li>
 *   <li>SELECT … FROM/JOIN t, UPDATE t, DELETE FROM t: 자바 SQL은 {@code tenant_id = :tenantId}, 마이그레이션 DML은 {@code tenant_id =}·{@code IN} 조건(D1·D3).</li>
 * </ul>
 * 제외 테이블은 {@code flyway_schema_history}뿐이다 — {@code tenant}도 자기 행 조건을 가진다(D2, 설계서 v1.2 S4).
 */
final class SqlTenantScanner {

    static final Set<String> EXCLUDED_TABLES = Set.of("flyway_schema_history");

    private static final Pattern CREATE_TABLE = Pattern.compile("\\bcreate\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z_][a-z0-9_]*)");
    private static final Pattern CREATE_INDEX = Pattern.compile("\\bcreate\\s+(?:unique\\s+)?index\\s+(?:if\\s+not\\s+exists\\s+)?[a-z0-9_]*\\s*on\\s+(?:only\\s+)?([a-z_][a-z0-9_]*)");
    private static final Pattern INSERT = Pattern.compile("\\binsert\\s+into\\s+([a-z_][a-z0-9_]*)\\s*(\\([^)]*\\))?");
    private static final Pattern UPDATE = Pattern.compile("\\bupdate\\s+(?:only\\s+)?([a-z_][a-z0-9_]*)\\s+(?:as\\s+)?(?:[a-z_][a-z0-9_]*\\s+)?set\\b");
    private static final Pattern DELETE = Pattern.compile("\\bdelete\\s+from\\s+(?:only\\s+)?([a-z_][a-z0-9_]*)");
    private static final Pattern FROM_JOIN = Pattern.compile("\\b(?:from|join)\\s+(?:only\\s+)?([a-z_][a-z0-9_]*)");
    private static final Pattern TENANT_COLUMN = Pattern.compile("\\btenant_id\\b");
    private static final Pattern TENANT_PREDICATE = Pattern.compile("\\btenant_id\\s*(?:=|\\bin\\b)|=\\s*[a-z0-9_.]*\\btenant_id\\b");
    /** 자바 저장소 SQL의 테넌트 술어: 정확히 {@code tenant_id = :tenantId}(별칭 허용, D3). */
    private static final Pattern BOUND_TENANT_PREDICATE = Pattern.compile("\\btenant_id\\s*=\\s*:tenantid\\b");
    private static final Pattern INSERT_VALUES = Pattern.compile("\\binsert\\s+into\\s+[a-z_][a-z0-9_]*\\s*\\(([^)]*)\\)\\s*values\\s*\\(");

    /** 스캔 방언: 자바 문자열 SQL(저장소 — :tenantId 바인드 규약) 또는 마이그레이션 .sql. */
    enum Dialect { JAVA, MIGRATION }
    private static final Pattern DOLLAR_TAG = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)?\\$");

    /** 발견한 위반. {@code statement}는 공백 정규화된 문장(허용 목록 키로도 쓴다). */
    record Violation(String source, String statement, String reason) {
        @Override
        public String toString() {
            return source + ": " + reason + "\n    " + statement;
        }
    }

    private SqlTenantScanner() {
    }

    /** 스크립트들에서 CREATE TABLE로 정의된 테이블 이름. */
    static Set<String> tablesDefinedIn(List<String> scripts) {
        Set<String> tables = new LinkedHashSet<>();
        for (String script : scripts) {
            for (String statement : statementsOf(script)) {
                Matcher m = CREATE_TABLE.matcher(statement);
                while (m.find()) {
                    tables.add(m.group(1));
                }
            }
        }
        return tables;
    }

    static List<Violation> scan(String source, String sql, Set<String> knownTables, Dialect dialect) {
        List<Violation> out = new ArrayList<>();
        for (String statement : statementsOf(sql)) {
            check(source, statement, knownTables, dialect, out);
        }
        return out;
    }

    /** 주석 제거·리터럴 비우기·달러 본문 분리 후 {@code ;} 단위 문장(소문자, 공백 정규화). 달러 본문의 문장도 포함한다. */
    static List<String> statementsOf(String sql) {
        List<String> bodies = new ArrayList<>();
        String flat = normalize(sql, bodies);
        List<String> out = new ArrayList<>();
        for (String piece : flat.split(";")) {
            String s = piece.strip().replaceAll("\\s+", " ");
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        for (String body : bodies) {
            out.addAll(statementsOf(body));
        }
        return out;
    }

    private static void check(String source, String statement, Set<String> knownTables, Dialect dialect, List<Violation> out) {
        Matcher createTable = CREATE_TABLE.matcher(statement);
        if (createTable.find()) {
            if (!EXCLUDED_TABLES.contains(createTable.group(1)) && !TENANT_COLUMN.matcher(statement).find()) {
                out.add(new Violation(source, statement, "CREATE TABLE " + createTable.group(1) + " has no tenant_id column"));
            }
            return;
        }
        Matcher createIndex = CREATE_INDEX.matcher(statement);
        if (createIndex.find()) {
            if (isTenantTable(createIndex.group(1), knownTables) && !TENANT_COLUMN.matcher(statement).find()) {
                out.add(new Violation(source, statement, "index on " + createIndex.group(1) + " does not include tenant_id"));
            }
            return;
        }
        Matcher insert = INSERT.matcher(statement);
        while (insert.find()) {
            if (isTenantTable(insert.group(1), knownTables)) {
                if (insert.group(2) == null || !TENANT_COLUMN.matcher(insert.group(2)).find()) {
                    out.add(new Violation(source, statement, "INSERT INTO " + insert.group(1) + " does not name tenant_id column"));
                } else if (dialect == Dialect.JAVA && !tenantValueIsBound(statement)) {
                    out.add(new Violation(source, statement, "INSERT INTO " + insert.group(1) + " does not bind :tenantId to tenant_id"));
                }
            }
        }
        Set<String> filtered = new LinkedHashSet<>();
        for (Pattern p : List.of(UPDATE, DELETE, FROM_JOIN)) {
            Matcher m = p.matcher(statement);
            while (m.find()) {
                if (isTenantTable(m.group(1), knownTables)) {
                    filtered.add(m.group(1));
                }
            }
        }
        Pattern predicate = dialect == Dialect.JAVA ? BOUND_TENANT_PREDICATE : TENANT_PREDICATE;
        if (!filtered.isEmpty() && !predicate.matcher(statement).find()) {
            out.add(new Violation(source, statement, "access to " + filtered + " without "
                    + (dialect == Dialect.JAVA ? "tenant_id = :tenantId" : "tenant_id") + " predicate"));
        }
    }

    /** {@code INSERT INTO t (c1, …, tenant_id, …) VALUES (v1, …)}에서 tenant_id 자리의 값이 {@code :tenantId}인가(D3). */
    private static boolean tenantValueIsBound(String statement) {
        Matcher m = INSERT_VALUES.matcher(statement);
        if (!m.find()) {
            return false;   // INSERT … SELECT 등 — 자바 저장소에서는 VALUES 형태만 받는다
        }
        List<String> columns = List.of(m.group(1).split("\\s*,\\s*")).stream().map(String::strip).toList();
        int index = columns.indexOf("tenant_id");
        List<String> values = splitTopLevel(statement.substring(m.end()));
        return index >= 0 && index < values.size() && values.get(index).strip().equals(":tenantid");
    }

    /** 닫는 괄호까지, 깊이 0의 쉼표로 나눈다. */
    private static List<String> splitTopLevel(String rest) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char ch : rest.toCharArray()) {
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (ch == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        parts.add(current.toString());
        return parts;
    }

    private static boolean isTenantTable(String table, Set<String> knownTables) {
        return knownTables.contains(table) && !EXCLUDED_TABLES.contains(table);
    }

    /**
     * 주석을 지우고, 작은따옴표 리터럴을 {@code ''}로, 큰따옴표 식별자는 그대로 두고, 달러 인용 본문은 {@code bodies}로 빼낸 뒤
     * 소문자로 돌려준다.
     */
    private static String normalize(String sql, List<String> bodies) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else if (c == '\'') {
                i++;
                while (i < n) {
                    if (sql.charAt(i) == '\'' && i + 1 < n && sql.charAt(i + 1) == '\'') {
                        i += 2;
                    } else if (sql.charAt(i) == '\'') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                out.append("''");
            } else if (c == '$') {
                Matcher tag = DOLLAR_TAG.matcher(sql).region(i, n);
                if (tag.lookingAt()) {
                    String delimiter = tag.group();
                    int bodyStart = i + delimiter.length();
                    int end = sql.indexOf(delimiter, bodyStart);
                    if (end < 0) {
                        end = n;
                    }
                    bodies.add(sql.substring(bodyStart, end));
                    out.append(" $body$ ");
                    i = Math.min(n, end + delimiter.length());
                } else {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /** 자바 소스에서 SQL로 보이는 문자열 리터럴·텍스트 블록(SELECT/INSERT/UPDATE/DELETE/WITH/MERGE로 시작)을 뽑는다. */
    static List<String> sqlLiteralsInJava(String java) {
        List<String> literals = new ArrayList<>();
        int i = 0;
        int n = java.length();
        while (i < n) {
            char c = java.charAt(i);
            if (c == '/' && i + 1 < n && java.charAt(i + 1) == '/') {
                while (i < n && java.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && java.charAt(i + 1) == '*') {
                int end = java.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (java.startsWith("\"\"\"", i)) {
                int end = java.indexOf("\"\"\"", i + 3);
                if (end < 0) {
                    end = n;
                }
                literals.add(java.substring(i + 3, end));
                i = Math.min(n, end + 3);
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && java.charAt(i) != '"') {
                    if (java.charAt(i) == '\\' && i + 1 < n) {
                        sb.append(java.charAt(i + 1));
                        i += 2;
                    } else {
                        sb.append(java.charAt(i++));
                    }
                }
                i++;
                literals.add(sb.toString());
            } else if (c == '\'') {
                // 문자 리터럴('"' 포함)을 건너뛴다.
                int end = java.indexOf('\'', i + (i + 1 < n && java.charAt(i + 1) == '\\' ? 3 : 2));
                i = end < 0 ? n : end + 1;
            } else {
                i++;
            }
        }
        Pattern sqlStart = Pattern.compile("(?is)^\\s*(select|insert|update|delete|with|merge)\\b.*");
        return literals.stream().filter(l -> sqlStart.matcher(l).matches()).toList();
    }
}

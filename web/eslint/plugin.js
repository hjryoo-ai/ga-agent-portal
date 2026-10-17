// 포털 화면 전용 ESLint 규칙(포털 Phase 4 계획 ④-b). 저장소 안 로컬 플러그인 — 규칙의 시험은 src/test/lintRules.test.ts(실제 설정으로 린트).
//  - no-money-arithmetic: 불투명 금액 타입 Won(src/money/won.ts)이 피연산자인 산술·비교·비트 연산, 단항·증감·복합 대입, Number()·String()·
//    parseInt·parseFloat·BigInt·Math.* 인자, 템플릿 문자열 보간, 그리고 Won을 다른 타입으로 바꾸는 형변환(as·<T>)을 금지한다(C2).
//    TS가 이미 산술을 막지만 형변환·비교·문자열 변환은 이 규칙이 막는다(형변환 뒤의 산술은 TS·이 규칙 모두 Won을 보지 못하므로 형변환 자체를 막는다).
//    예외는 MONEY_EXEMPT(닫힌 파일 목록) — 정수 형식화와 0 판정만 하는 src/money/won.ts.
//  - no-hangul-literal: 한글이 든 문자열 리터럴·템플릿·JSX 텍스트는 messages.ko.json 밖에 둘 수 없다(상태 라벨은 데이터 — Phase 3 수용 심사 Q1).
//    주석은 대상이 아니다.
import { relative, sep } from 'node:path';

export const MONEY_EXEMPT = ['src/money/won.ts'];
const MONEY_FILE = `${sep}src${sep}money${sep}won.ts`;
const ARITH = new Set(['+', '-', '*', '/', '%', '**', '<', '>', '<=', '>=', '==', '===', '!=', '!==', '|', '&', '^', '<<', '>>', '>>>']);
const CONVERTERS = new Set(['Number', 'String', 'parseInt', 'parseFloat', 'BigInt']);
const HANGUL = /[ᄀ-ᇿ㄰-㆏가-힣]/;

function isWonType(type) {
  if (type.isUnion() || type.isIntersection()) return type.types.some(isWonType);
  const symbol = type.aliasSymbol ?? type.getSymbol();
  if (!symbol || symbol.getName() !== 'Won') return false;
  return (symbol.getDeclarations() ?? []).some((d) => d.getSourceFile().fileName.replaceAll('/', sep).endsWith(MONEY_FILE));
}

const noMoneyArithmetic = {
  meta: {
    type: 'problem',
    docs: { description: 'Money (Won) is never computed, compared or converted on screen.' },
    messages: {
      money: 'Money (Won) is not a number on screen: no arithmetic, comparison or conversion ({{what}}). Render it with <Amount> (Phase 4 C2).',
    },
    schema: [],
  },
  create(context) {
    const rel = relative(context.cwd, context.filename).split(sep).join('/');
    if (MONEY_EXEMPT.includes(rel)) return {};
    const services = context.sourceCode.parserServices;
    if (!services?.program) throw new Error('portal/no-money-arithmetic needs type information (parserOptions.projectService)');
    const checker = services.program.getTypeChecker();
    const isWon = (node) => node != null && node.type !== 'PrivateIdentifier' && isWonType(checker.getTypeAtLocation(services.esTreeNodeToTSNodeMap.get(node)));
    const report = (node, what) => context.report({ node, messageId: 'money', data: { what } });
    return {
      BinaryExpression(node) {
        if (ARITH.has(node.operator) && (isWon(node.left) || isWon(node.right))) report(node, `operator ${node.operator}`);
      },
      UnaryExpression(node) {
        if (['-', '+', '~'].includes(node.operator) && isWon(node.argument)) report(node, `unary ${node.operator}`);
      },
      UpdateExpression(node) {
        if (isWon(node.argument)) report(node, node.operator);
      },
      AssignmentExpression(node) {
        if (node.operator !== '=' && (isWon(node.left) || isWon(node.right))) report(node, node.operator);
      },
      CallExpression(node) {
        const c = node.callee;
        const converter = c.type === 'Identifier' && CONVERTERS.has(c.name);
        const math = c.type === 'MemberExpression' && c.object.type === 'Identifier' && c.object.name === 'Math';
        if ((converter || math) && node.arguments.some((a) => isWon(a))) report(node, converter ? `${c.name}()` : 'Math.*');
      },
      'TSAsExpression, TSTypeAssertion'(node) {
        if (isWon(node.expression) && !isWon(node)) report(node, 'type cast');
      },
      TemplateLiteral(node) {
        if (node.expressions.some((e) => isWon(e))) report(node, 'template string');
      },
    };
  },
};

const noHangulLiteral = {
  meta: {
    type: 'problem',
    docs: { description: 'User-visible Korean text lives in src/messages.ko.json only.' },
    messages: { hangul: 'Korean text belongs in src/messages.ko.json (labels are data — Phase 3 acceptance Q1).' },
    schema: [],
  },
  create(context) {
    const check = (node, text) => {
      if (typeof text === 'string' && HANGUL.test(text)) context.report({ node, messageId: 'hangul' });
    };
    return {
      Literal(node) {
        check(node, node.value);
      },
      TemplateElement(node) {
        check(node, node.value.cooked ?? node.value.raw);
      },
      JSXText(node) {
        check(node, node.value);
      },
    };
  },
};

export default {
  meta: { name: 'eslint-plugin-portal' },
  rules: { 'no-money-arithmetic': noMoneyArithmetic, 'no-hangul-literal': noHangulLiteral },
};

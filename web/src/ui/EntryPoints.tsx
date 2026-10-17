// 비활성 진입점(포털 Phase 4 지시문 ② — 분급 타임라인·시뮬레이션은 Phase 5, 알림함은 Phase 6). "알림 없음" 같은 빈 상태를 말하지 않는다(계획 Q7).
import { m } from '../messages';

export function ComingSoon({ name, testId }: { name: string; testId: string }) {
  return (
    <button type="button" className="button button--disabled" disabled data-testid={testId}>
      <span>{name}</span>
      <span className="badge">{m.nav.comingSoon}</span>
    </button>
  );
}

// 소진 바(계획 P-11): utilization은 API 표시 문자열("60.0%")이다 — 숫자로 바꾸지 않고 CSS 사용자 속성으로 너비를 준다(CSSOM 설정이라 CSP
// style-src 'self'와 맞는다). 접근성은 문자열 그대로의 이름.
import type { CSSProperties } from 'react';
import { fill, m } from '../messages';

export function Utilization({ value }: { value: string }) {
  const style = { '--util': value } as CSSProperties;
  const text = fill(m.limits.utilization, { pct: value });
  return (
    <div className="util" data-testid="utilization">
      <div className="util__bar" role="img" aria-label={text}>
        <div className="util__fill" style={style} />
      </div>
      <span className="util__text">{text}</span>
    </div>
  );
}

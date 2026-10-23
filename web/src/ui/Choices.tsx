// 고르기(포털 Phase 5 — 계약·변경 유형): 누름 상태 버튼 묶음(aria-pressed). <select>는 쓰지 않는다 — WebKit의 목록 상자가 엄격한 CSP 아래에서
// style-src-attr 위반을 낸다(E2E mobile-ios — 같은 화면의 select 수만큼). 기간 고르기(분할 컨트롤)와 같은 모양, 버튼은 44px.
import { useId, type ReactNode } from 'react';

export interface Choice {
  value: string;
  name: string;
}

export function Choices({
  label,
  options,
  value,
  onChange,
  testId,
  children,
}: {
  label: string;
  options: readonly Choice[];
  value: string;
  onChange: (value: string) => void;
  testId: string;
  children?: ReactNode;
}) {
  const id = useId();
  return (
    <div className="field" data-testid={testId}>
      <span id={id}>{label}</span>
      <div className="segmented segmented--wrap" role="group" aria-labelledby={id}>
        {options.map((o) => (
          <button key={o.value} type="button" className="button" aria-pressed={value === o.value} data-value={o.value} onClick={() => {
              onChange(o.value);
            }}>
            {o.name}
          </button>
        ))}
      </div>
      {children}
    </div>
  );
}

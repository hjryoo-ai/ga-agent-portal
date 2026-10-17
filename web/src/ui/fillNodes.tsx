// 문구 안에 요소(금액 등)를 끼운다 — "정정 {amount} — 지급 반영은 정산팀 확인"의 {amount} 자리에 <Amount>(라벨 포함)를 넣는다.
import { Fragment, type ReactNode } from 'react';

export function fillNodes(template: string, values: Record<string, ReactNode>): ReactNode {
  const parts = template.split(/(\{\w+\})/);
  return parts.map((part, i) => {
    const name = /^\{(\w+)\}$/.exec(part)?.[1];
    const node = name === undefined ? part : (values[name] ?? part);
    return <Fragment key={i}>{node}</Fragment>;
  });
}

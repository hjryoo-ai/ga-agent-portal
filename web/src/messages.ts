// 화면 문구는 전부 이 데이터에서 온다(Phase 3 수용 심사 Q1 — 상태 라벨은 데이터, 한국어 하나).
import messages from './messages.ko.json';

export type Messages = typeof messages;
export const m: Messages = messages;

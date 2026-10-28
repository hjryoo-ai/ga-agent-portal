// eslint.config.js의 타입(시험이 닫힌 네트워크 예외 목록과 블록들을 읽는다 — 포털 Phase 8).
import type { Linter } from 'eslint';

export declare const NETWORK_EXCEPTIONS: readonly string[];
declare const config: Linter.Config[];
export default config;

// 생성물 — 손으로 고치지 않는다. 계약 contracts/api/v1/portal-api.openapi.yaml → scripts/client-lock.mjs(./gradlew :web:clientLock).
// 금액 스키마(Won·NonNegativeWon)는 불투명 타입 Won이다(src/money/won.ts).
import type { Won } from '../money/won';

export interface paths {
    "/api/v1/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 본인 프로필·역할·테넌트 설정 */
        get: operations["getMe"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/deferrals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 분급 전망(지급 예정월별 합과 항목) */
        get: operations["listDeferrals"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/earnings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 월별 수수료 요약(범위) */
        get: operations["listEarnings"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/earnings/{ym}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 월 하나 — 유형별 합·명세·지급 확정값·지급 예정일 */
        get: operations["getMonth"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/earnings/{ym}/lines": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 월의 라인(페이지) — 평평하게, 취소 짝은 reversalOf·reversedBy */
        get: operations["listLines"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/earnings/lines/{calcId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 라인 상세 — 근거·계약 요약·분급·채권·한도 원장 */
        get: operations["getLine"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/limits": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 한도 원장 카드(1200%룰) */
        get: operations["listLimits"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/limits/{ledgerId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 한도 원장 하나와 타임라인 */
        get: operations["getLimit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/simulations/new-contract": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 신계약 시뮬레이션(1.1.0) — 엔진 dry-run 결과를 그대로(설계사 참고용·비확정) */
        post: operations["simulateNewContract"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/simulations/policy-event": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 해약 등 계약 이벤트 영향 시뮬레이션(1.1.0) — 엔진 dry-run 결과를 그대로 */
        post: operations["simulatePolicyEvent"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        AsOf: {
            at?: components["schemas"]["Instant"];
            seq: components["schemas"]["Seq"];
        };
        /**
         * @description 동기화 상태 — 커서가 RUNNING이 아니거나 머리에 닿은 지 sync_delay_seconds를 넘으면 SYNC_DELAYED
         * @enum {string}
         */
        DataStatus: "OK" | "SYNC_DELAYED";
        Date: string;
        /** @description 불투명 소수 문자열(표시 전용 — 계산·비교하지 않는다) */
        DecimalString: string;
        DeferralsResponse: {
            asOf: components["schemas"]["AsOf"];
            dataStatus: components["schemas"]["DataStatus"];
            from: components["schemas"]["Ym"];
            items: {
                amount: components["schemas"]["Won"];
                dueYm: components["schemas"]["Ym"];
                payCondition: string;
                policyKey: components["schemas"]["PolicyKey"];
                policyMasked: components["schemas"]["PolicyMasked"];
                /** @enum {string} */
                reason: "CURVE" | "LIMIT_DEFER";
                scheduleId: components["schemas"]["Key"];
                sourceCalcId: components["schemas"]["Key"];
                /** @enum {string} */
                status: "SCHEDULED" | "RELEASED" | "HELD" | "CANCELLED";
                /** @enum {string} */
                statusReason?: "DUE_RELEASED" | "POLICY_NOT_INFORCE" | "POLICY_TERMINATED" | "SOURCE_REVERSED";
            }[];
            months: {
                /** @description 막대 너비(1.1.0) — 값 / 범위 안 max(expected + held). max ≤ 0이면 생략 */
                bar?: {
                    expected: components["schemas"]["Percent"];
                    held: components["schemas"]["Percent"];
                };
                dueYm: components["schemas"]["Ym"];
                /** @description Σ SCHEDULED + RELEASED. RETENTION이면 floor(expectedBase × p / 100) — 포털의 유일한 금액 곱셈(설계서 §6.4) */
                expected: components["schemas"]["Won"];
                /** @description RETENTION일 때 적용 전 예정(1.1.0) */
                expectedBase?: components["schemas"]["Won"];
                /** @description Σ HELD(시나리오와 무관 — 원값) */
                held: components["schemas"]["Won"];
            }[];
            /** @description RETENTION의 유지율 가정(1.1.0) */
            p?: number;
            /**
             * @description 응답에 쓰인 시나리오(1.1.0 — 언제나 있다)
             * @enum {string}
             */
            scenario?: "ALL_INFORCE" | "RETENTION";
            to: components["schemas"]["Ym"];
        };
        /** @description 요청 본문의 횟수 1~9999(숫자 문자열, 1.1.0) */
        DigitCount: string;
        /** @description 요청 본문의 정수 원(숫자 문자열, 1.1.0 — 서버가 정수로 읽는다) */
        DigitWon: string;
        EarningsResponse: {
            asOf: components["schemas"]["AsOf"];
            dataStatus: components["schemas"]["DataStatus"];
            from: components["schemas"]["Ym"];
            items: components["schemas"]["MonthSummary"][];
            to: components["schemas"]["Ym"];
        };
        /** @description 엔진이 발급한 식별자(엔진 계약 defs id와 같은 규칙) */
        EngineId: string;
        Instant: string;
        Key: string;
        Ledger: {
            accumPaid: components["schemas"]["Won"];
            contractDate: components["schemas"]["Date"];
            cutTotal: components["schemas"]["NonNegativeWon"];
            deferredTotal: components["schemas"]["NonNegativeWon"];
            flags: ("CANCELLED" | "LAPSED" | "OVERDUE" | "REDUCED")[];
            fyEnd: components["schemas"]["Date"];
            fyStart: components["schemas"]["Date"];
            insuredMasked?: string;
            ledgerId: components["schemas"]["Key"];
            limitAmount: components["schemas"]["NonNegativeWon"];
            limitMultiple: components["schemas"]["DecimalString"];
            monthlyPremium: components["schemas"]["NonNegativeWon"];
            policyKey: components["schemas"]["PolicyKey"];
            policyMasked: components["schemas"]["PolicyMasked"];
            /** @description 그 계약의 첫 라인의 상품 키(1.1.0, CR-4) */
            productKey?: string;
            productName?: string;
            /** @description 한도 − 누적(정수 차감) */
            remaining: components["schemas"]["Won"];
            /** @description 원장을 연 한도 룰 버전(1.1.0, CR-3) */
            ruleVersionId?: string;
            /**
             * @description 테넌트 임계치(bp)와의 정수 비교
             * @enum {string}
             */
            stage: "OK" | "WARN" | "CRIT" | "FULL";
            /** @description 누적/한도 표시 문자열(한도 0이면 생략) */
            utilization?: components["schemas"]["Percent"];
        };
        LimitDetailResponse: {
            asOf: components["schemas"]["AsOf"];
            dataStatus: components["schemas"]["DataStatus"];
            history: {
                accumAfter: components["schemas"]["Won"];
                amount?: components["schemas"]["Won"];
                calcId?: components["schemas"]["Key"];
                /** @enum {string} */
                kind: "POST" | "REBASE";
                limitAfter: components["schemas"]["NonNegativeWon"];
                occurredAt: components["schemas"]["Instant"];
                postingSeq?: components["schemas"]["Seq"];
                reason?: string;
                sourceSeq: components["schemas"]["Seq"];
            }[];
            ledger: components["schemas"]["Ledger"];
        };
        LimitsResponse: {
            asOf: components["schemas"]["AsOf"];
            dataStatus: components["schemas"]["DataStatus"];
            items: components["schemas"]["Ledger"][];
            /** @enum {string} */
            status: "ALL" | "WARN" | "CRIT" | "FULL";
        };
        Line: {
            amount: components["schemas"]["Won"];
            calcId: components["schemas"]["Key"];
            closeYm: components["schemas"]["Ym"];
            commType: string;
            insuredMasked?: string;
            limitCutAmt: components["schemas"]["NonNegativeWon"];
            limitCutPolicy?: string;
            policyKey: components["schemas"]["PolicyKey"];
            policyMasked: components["schemas"]["PolicyMasked"];
            productKey?: string;
            productName?: string;
            reversalOf?: components["schemas"]["Key"];
            /** @description reversalOf 라인의 마감월(1.1.0, CR-2) */
            reversalOfYm?: components["schemas"]["Ym"];
            reversedBy?: components["schemas"]["Key"];
            /** @description reversedBy 라인의 마감월(1.1.0, CR-2) */
            reversedByYm?: components["schemas"]["Ym"];
        };
        LineDetailResponse: {
            asOf: components["schemas"]["AsOf"];
            basis: {
                installmentNo?: number;
                payoutRate?: components["schemas"]["DecimalString"];
                rate?: components["schemas"]["DecimalString"];
                ruleVersionIds: string[];
            };
            dataStatus: components["schemas"]["DataStatus"];
            deferrals: {
                amount: components["schemas"]["Won"];
                dueYm: components["schemas"]["Ym"];
                scheduleId: components["schemas"]["Key"];
                /** @enum {string} */
                status: "SCHEDULED" | "RELEASED" | "HELD" | "CANCELLED";
            }[];
            ledgerId?: components["schemas"]["Key"];
            line: components["schemas"]["Line"];
            policySummary: {
                contractDate?: components["schemas"]["Date"];
                monthlyPremium?: components["schemas"]["NonNegativeWon"];
            };
            receivable?: {
                amount: components["schemas"]["NonNegativeWon"];
                receivableId: components["schemas"]["Key"];
                remaining: components["schemas"]["NonNegativeWon"];
                /** @enum {string} */
                status: "OPEN" | "OFFSET" | "CLOSED" | "WRITTEN_OFF";
            };
        };
        LinesResponse: {
            asOf: components["schemas"]["AsOf"];
            closeYm: components["schemas"]["Ym"];
            dataStatus: components["schemas"]["DataStatus"];
            items: components["schemas"]["Line"][];
            page: number;
            size: number;
            total: number;
        };
        MeResponse: {
            /** @description 본인 설계사 ID(identity_link) */
            agentId: string;
            /** @description 본인 이름(가리지 않는다 — DD-13) */
            agentName?: string;
            asOf: components["schemas"]["AsOf"];
            dataStatus: components["schemas"]["DataStatus"];
            /** @description 테넌트가 켠 화면 기능(1.1.0) */
            features?: {
                /** @description 분급 타임라인의 유지율 시나리오(RETENTION)를 쓸 수 있다 */
                forecastScenario: boolean;
            };
            grade?: string;
            orgPath?: string;
            role: components["schemas"]["Role"];
            /** @enum {string} */
            status?: "ACTIVE" | "DISMISSED";
            tenant: {
                limitCritBp: number;
                /** @description 경고 임계치(백분율 × 100) */
                limitWarnBp: number;
                name: string;
                payoutDayRule: string;
            };
        };
        /**
         * @description 그 달의 대사 결과(동기화 상태와 따로 — DD-12)
         * @enum {string}
         */
        MonthDataStatus: "OK" | "DRIFT" | "REBUILDING";
        MonthResponse: {
            adjustment: components["schemas"]["Won"];
            asOf: components["schemas"]["AsOf"];
            /** @description 수수료 유형별 라인 합(정수 원) */
            byType: {
                [key: string]: components["schemas"]["Won"];
            };
            carriedReceivable?: components["schemas"]["Won"];
            clawbackOffset: components["schemas"]["Won"];
            closeYm: components["schemas"]["Ym"];
            confirmedAt?: components["schemas"]["Instant"];
            dataStatus: components["schemas"]["DataStatus"];
            estimateAsOf?: components["schemas"]["Instant"];
            estimateBasisSeq?: components["schemas"]["Seq"];
            gross: components["schemas"]["Won"];
            monthDataStatus: components["schemas"]["MonthDataStatus"];
            net?: components["schemas"]["Won"];
            netBasis?: components["schemas"]["NetBasis"];
            paidAmount?: components["schemas"]["Won"];
            paidAt?: components["schemas"]["Instant"];
            payable?: components["schemas"]["Won"];
            /** @description 테넌트 지급일 규칙(NEXT_MONTH_D{n}) — 형식이 다르거나 그 날이 없으면 생략 */
            payoutExpectedOn?: components["schemas"]["Date"];
            /** @description 지난달 행의 값(1.1.0, CR-1 — 그 달의 상태 그대로, 차감하지 않는다). 지난달 행이 없으면 생략 */
            previous?: {
                closeYm: components["schemas"]["Ym"];
                gross: components["schemas"]["Won"];
                net?: components["schemas"]["Won"];
                netBasis?: components["schemas"]["NetBasis"];
                status: components["schemas"]["MonthStatus"];
            };
            receivableOffset?: components["schemas"]["Won"];
            runSeq?: components["schemas"]["Seq"];
            status: components["schemas"]["MonthStatus"];
            withholdingTax?: components["schemas"]["Won"];
        };
        /** @enum {string} */
        MonthStatus: "PROVISIONAL" | "ESTIMATED" | "CONFIRMED" | "PAID";
        MonthSummary: {
            adjustment: components["schemas"]["Won"];
            clawbackOffset: components["schemas"]["Won"];
            closeYm: components["schemas"]["Ym"];
            gross: components["schemas"]["Won"];
            monthDataStatus: components["schemas"]["MonthDataStatus"];
            net?: components["schemas"]["Won"];
            netBasis?: components["schemas"]["NetBasis"];
            paidAmount?: components["schemas"]["Won"];
            paidAt?: components["schemas"]["Instant"];
            status: components["schemas"]["MonthStatus"];
            withholdingTax?: components["schemas"]["Won"];
        };
        /** @enum {string} */
        NetBasis: "ESTIMATE" | "CONFIRM" | "PAYOUT";
        NonNegativeWon: Won;
        /** @description 표시용 백분율(소수 1자리) */
        Percent: string;
        /** @description HMAC-SHA256(배포 비밀, tenant_id + U+001F + policy_no) 앞 16자 — 비가역, 비밀 회전 시 바뀐다 */
        PolicyKey: string;
        /** @description 증권번호의 끝 4자 외 '*'(4자 이하면 전부 '*') */
        PolicyMasked: string;
        Problem: {
            /** @enum {string} */
            code: "UNAUTHENTICATED" | "NOT_FOUND" | "INVALID_REQUEST" | "INTERNAL_ERROR" | "INSURER_AMBIGUOUS" | "UNSUPPORTED_EVENT_TYPE" | "ENGINE_REJECTED" | "RATE_LIMITED" | "ENGINE_CONTRACT" | "ENGINE_UNAVAILABLE";
            message: string;
        };
        /** @enum {string} */
        Role: "AGENT" | "MANAGER" | "OPERATOR";
        Seq: number;
        /** @description simulation_log 행의 키(UUID) — 문의·감사용 */
        SimId: string;
        /** @description 신계약 시뮬레이션 요청(1.1.0). 설계사는 토큰·identity_link로 정한다. 시책 코드는 받지 않는다 — 엔진이 그 계약에 맞는 시책을 전부 적용한다 */
        SimNewContractRequest: {
            contractDate: components["schemas"]["Date"];
            /** @description 보험사 코드 — 422 INSURER_AMBIGUOUS를 받은 뒤에만 준다 */
            insurerCd?: components["schemas"]["EngineId"];
            monthlyPremium: components["schemas"]["DigitWon"];
            paymentTermMonths?: components["schemas"]["DigitCount"];
            productKey: components["schemas"]["EngineId"];
        };
        /** @description 엔진 NewContractResult(엔진 계약 사본 1.4.0)의 속성을 값 그대로 + 포털 사실 넷. 엔진 필수 목록과 같다(SimulationSchemaParityTest) */
        SimNewContractResponse: {
            asOf: components["schemas"]["AsOf"];
            dataStatus: components["schemas"]["DataStatus"];
            deferral: {
                firstYearPayout: components["schemas"]["Won"];
                schedule: {
                    amount: components["schemas"]["Won"];
                    dueYm: components["schemas"]["Ym"];
                }[];
            };
            fyCommissionByInstallment: {
                amount: components["schemas"]["Won"];
                no: number;
            }[];
            /** @description 엔진 산출 시각(엔진 값 그대로) */
            generatedAt: components["schemas"]["Instant"];
            incentives: {
                amount: components["schemas"]["Won"];
                code: string;
                limitCutAmt?: components["schemas"]["NonNegativeWon"];
                limitIncluded: boolean;
            }[];
            limitAmount?: components["schemas"]["NonNegativeWon"];
            projectedAccum?: components["schemas"]["Won"];
            /** @description 포털이 요청을 기록한 시각(simulation_log.created_at) */
            requestedAt: components["schemas"]["Instant"];
            ruleSnapshotId: components["schemas"]["EngineId"];
            simId: components["schemas"]["SimId"];
        };
        /** @description 계약 이벤트 시뮬레이션 요청(1.1.0). 계약은 policyKey로 — 그 설계사의 계약이 아니면 404(엔진 호출 없음). REDUCE면 newMonthlyPremium 필수 */
        SimPolicyEventRequest: {
            asOfDate: components["schemas"]["Date"];
            /** @description 경과 회차 — 없으면 엔진이 체결일과 asOfDate의 개월 수로 근사한다 */
            elapsedInstallments?: components["schemas"]["DigitCount"];
            /** @enum {string} */
            eventType: "CANCEL" | "LAPSE" | "WITHDRAW" | "REDUCE" | "REINSTATE" | "PAYMENT_OVERDUE";
            /** @description 감액 뒤 월납(REDUCE일 때만 필수) */
            newMonthlyPremium?: components["schemas"]["DigitWon"];
            policyKey: components["schemas"]["PolicyKey"];
        };
        /** @description 엔진 PolicyEventResult(엔진 계약 사본 1.4.0)의 속성을 값 그대로 + 포털 사실 넷. 엔진 필수 목록과 같다(SimulationSchemaParityTest) */
        SimPolicyEventResponse: {
            asOf: components["schemas"]["AsOf"];
            cancelledDeferrals: {
                amount: components["schemas"]["Won"];
                dueYm: components["schemas"]["Ym"];
                scheduleId: components["schemas"]["EngineId"];
            }[];
            /** @description 환수(해약·실효는 ≤ 0, 부활 재지급은 > 0 — 엔진 부호 그대로) */
            clawbackAmount: components["schemas"]["Won"];
            dataStatus: components["schemas"]["DataStatus"];
            /** @description 환수가 귀속되는 달(closeYm)의 세전·채권 상계 전 순액(gross + clawbackOffset)의 전후 — 이번 달이 아닐 수 있다 */
            offsetAgainstMonth: {
                after: components["schemas"]["Won"];
                before: components["schemas"]["Won"];
                closeYm: components["schemas"]["Ym"];
            };
            receivableRemaining: components["schemas"]["Won"];
            requestedAt: components["schemas"]["Instant"];
            ruleSnapshotId: components["schemas"]["EngineId"];
            simId: components["schemas"]["SimId"];
        };
        /** @description 정수 원(부호 있음 — 환수·취소는 음수) */
        Won: Won;
        Ym: string;
    };
    responses: {
        /** @description 엔진 응답이 계약과 다르다 — 스키마 위반·예상 밖 상태·엔진 400·404(1.1.0). code ENGINE_CONTRACT, 재시도하지 않는다 */
        EngineContract: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description 엔진에 닿지 못했다 — 연결 실패·시간 초과·엔진 5xx·서비스 토큰 거부(1.1.0). code ENGINE_UNAVAILABLE, 조회 API는 영향 없음 */
        EngineUnavailable: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description 쿼리 파라미터 형식·범위 오류, 시뮬레이션 요청 본문의 형식·크기·스키마 오류(사유 없음) */
        InvalidRequest: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description 링크 없음·REVOKED·역할·남의(없는) 자원·없는 경로 — 사유와 무관하게 같은 바이트 */
        NotFound: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description If-None-Match가 현재 ETag와 맞다(본문 없음, 같은 ETag·Cache-Control) */
        NotModified: {
            headers: {
                [name: string]: unknown;
            };
            content?: never;
        };
        /** @description 그 설계사의 최근 60초 시뮬레이션 요청 수가 테넌트 한도 이상(1.1.0) — code RATE_LIMITED, 행·엔진 호출 없음 */
        RateLimited: {
            headers: {
                /** @description 창 안 가장 오래된 요청이 빠질 때까지의 초(올림, 1 이상) */
                "Retry-After"?: string;
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /**
         * @description 엔진이 거부했다(1.1.0) — code INSURER_AMBIGUOUS(보험사를 정해야 한다 — insurerCd를 주고 다시), UNSUPPORTED_EVENT_TYPE(엔진이 시뮬레이션하지 않는
         *     유형), ENGINE_REJECTED(그 밖의 엔진 422). 코드마다 포털 고정 바이트 — 엔진의 message는 옮기지 않는다.
         */
        SimRejected: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description 토큰 없음·형식·알고리즘·발급자·서명·만료·대상·테넌트 교차 검사 — 사유와 무관하게 같은 바이트 */
        Unauthenticated: {
            headers: {
                "WWW-Authenticate"?: "Bearer";
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
    };
    parameters: {
        /** @description 시작 지급월 YYYYMM(포함). to와 함께 필수, from ≤ to, 폭 84개월 이하(1.1.0 — 분급 타임라인 7년) */
        DeferralFrom: components["schemas"]["Ym"];
        /** @description 끝 지급월 YYYYMM(포함) */
        DeferralTo: components["schemas"]["Ym"];
        /** @description 시작 월 YYYYMM(포함). to와 함께 필수, from ≤ to, 폭 36개월 이하 */
        From: components["schemas"]["Ym"];
        /** @description 끝 월 YYYYMM(포함) */
        To: components["schemas"]["Ym"];
        /** @description 마감월 YYYYMM(형식이 틀리면 404 — 존재 여부를 형식 오류로 흘리지 않는다) */
        Ym: components["schemas"]["Ym"];
    };
    requestBodies: never;
    headers: {
        CacheControl: "private, no-cache";
        /** @description 약한 ETag W/"<seq>.<sync>.<cfg>"(라인 목록은 뒤에 .<page>.<size>.<filter>) — 피드 위치·동기화 상태·응답에 쓰인 테넌트 설정이 바뀌면 바뀐다 */
        ETag: string;
        /** @description 시뮬레이션 결과는 저장하지 않는다(1.1.0) */
        NoStore: "no-store";
    };
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    getMe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 프로필(agent_profile 행이 없으면 이름·등급·조직·상태 생략) */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MeResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    listDeferrals: {
        parameters: {
            query: {
                /** @description 시작 지급월 YYYYMM(포함). to와 함께 필수, from ≤ to, 폭 84개월 이하(1.1.0 — 분급 타임라인 7년) */
                from: components["parameters"]["DeferralFrom"];
                /** @description 유지율 가정 백분율 1~100(1.1.0) — scenario=RETENTION이면 필수, 아니면 주면 400 */
                p?: string;
                /** @description 그 설계사의 계약 하나로 월 합계·항목·막대를 낸다(1.1.0 — 모르는 키는 빈 결과) */
                policyKey?: components["schemas"]["PolicyKey"];
                /** @description 전망 시나리오(1.1.0, 기본 ALL_INFORCE). RETENTION은 테넌트가 켰을 때만(아니면 400) */
                scenario?: "ALL_INFORCE" | "RETENTION";
                /** @description 끝 지급월 YYYYMM(포함) */
                to: components["parameters"]["DeferralTo"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 지급월별 예정(SCHEDULED+RELEASED)·보류(HELD) 합과 항목 */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeferralsResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            400: components["responses"]["InvalidRequest"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    listEarnings: {
        parameters: {
            query: {
                /** @description 시작 월 YYYYMM(포함). to와 함께 필수, from ≤ to, 폭 36개월 이하 */
                from: components["parameters"]["From"];
                /** @description 끝 월 YYYYMM(포함) */
                to: components["parameters"]["To"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 그 범위의 월(마감월 오름차순). 행이 없으면 빈 목록 */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EarningsResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            400: components["responses"]["InvalidRequest"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    getMonth: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 마감월 YYYYMM(형식이 틀리면 404 — 존재 여부를 형식 오류로 흘리지 않는다) */
                ym: components["parameters"]["Ym"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 월 하나 */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MonthResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    listLines: {
        parameters: {
            query?: {
                /** @description 수수료 유형 정확 일치 */
                commType?: string;
                /** @description 0부터(기본 0) */
                page?: number;
                /** @description 응답의 policyKey(그 설계사의 계약만 풀린다 — 아니면 빈 목록) */
                policyKey?: components["schemas"]["PolicyKey"];
                /** @description 1~200(기본 50) */
                size?: number;
            };
            header?: never;
            path: {
                /** @description 마감월 YYYYMM(형식이 틀리면 404 — 존재 여부를 형식 오류로 흘리지 않는다) */
                ym: components["parameters"]["Ym"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 한 페이지(정렬 source_seq, calc_id)와 필터에 맞는 전체 수 */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LinesResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            400: components["responses"]["InvalidRequest"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    getLine: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                calcId: components["schemas"]["Key"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 라인 상세 */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LineDetailResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    listLimits: {
        parameters: {
            query?: {
                /** @description 경고 단계(기본 ALL) */
                status?: "ALL" | "WARN" | "CRIT" | "FULL";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 원장 카드(원장 ID 순) */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LimitsResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            400: components["responses"]["InvalidRequest"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    getLimit: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                ledgerId: components["schemas"]["Key"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 원장 카드 + 타임라인(source_seq 순) */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["CacheControl"];
                    ETag: components["headers"]["ETag"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LimitDetailResponse"];
                };
            };
            304: components["responses"]["NotModified"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
        };
    };
    simulateNewContract: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SimNewContractRequest"];
            };
        };
        responses: {
            /** @description 엔진 NewContractResult의 속성 + 포털 사실(asOf·dataStatus·simId·requestedAt) */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SimNewContractResponse"];
                };
            };
            400: components["responses"]["InvalidRequest"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
            422: components["responses"]["SimRejected"];
            429: components["responses"]["RateLimited"];
            502: components["responses"]["EngineContract"];
            503: components["responses"]["EngineUnavailable"];
        };
    };
    simulatePolicyEvent: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SimPolicyEventRequest"];
            };
        };
        responses: {
            /** @description 엔진 PolicyEventResult의 속성 + 포털 사실(asOf·dataStatus·simId·requestedAt) */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SimPolicyEventResponse"];
                };
            };
            400: components["responses"]["InvalidRequest"];
            401: components["responses"]["Unauthenticated"];
            404: components["responses"]["NotFound"];
            422: components["responses"]["SimRejected"];
            429: components["responses"]["RateLimited"];
            502: components["responses"]["EngineContract"];
            503: components["responses"]["EngineUnavailable"];
        };
    };
}

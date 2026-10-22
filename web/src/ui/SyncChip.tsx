// 동기화 칩(포털 Phase 4 지시문 ②·③): 화면에 그려진 응답들의 asOf·dataStatus를 나쁜 쪽으로 합친다 — 하나라도 SYNC_DELAYED면 지연,
// 기준은 가장 작은 seq의 시각(정수 비교 — 금액 아님). 응답은 지금 화면이 쓰는(관찰자가 있는) 쿼리의 것만.
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';
import { fill, m } from '../messages';
import { formatAt } from '../time/kst';

interface SyncFacts {
  asOf: { seq: number; at?: string };
  dataStatus: 'OK' | 'SYNC_DELAYED';
}

function isSyncFacts(value: unknown): value is SyncFacts {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as { asOf?: unknown; dataStatus?: unknown };
  return typeof v.asOf === 'object' && v.asOf !== null && (v.dataStatus === 'OK' || v.dataStatus === 'SYNC_DELAYED');
}

/** 칩 상태를 원시 문자열로(useSyncExternalStore는 같은 값이면 다시 그리지 않는다). */
export function syncKey(client: QueryClient): string {
  let delayed = false;
  let worst: SyncFacts['asOf'] | null = null;
  for (const q of client.getQueryCache().getAll()) {
    const data: unknown = q.state.data;
    if (q.getObserversCount() === 0 || !isSyncFacts(data)) continue;
    delayed ||= data.dataStatus === 'SYNC_DELAYED';
    if (worst === null || data.asOf.seq < worst.seq) worst = data.asOf;
  }
  if (worst === null) return '';
  return JSON.stringify({ delayed, seq: worst.seq, at: worst.at ?? null });
}

export function syncText(key: string): string {
  if (key === '') return '';
  const s = JSON.parse(key) as { delayed: boolean; seq: number; at: string | null };
  if (s.at === null) return m.sync.none;
  return fill(s.delayed ? m.sync.delayed : m.sync.ok, { at: formatAt(s.at) });
}

export function SyncChip() {
  const client = useQueryClient();
  const key = useSyncExternalStore(
    (onChange) => client.getQueryCache().subscribe(onChange),
    () => syncKey(client),
  );
  if (key === '') return null;
  const delayed = (JSON.parse(key) as { delayed: boolean }).delayed;
  return (
    <p className={delayed ? 'sync-chip sync-chip--delayed' : 'sync-chip'} role="status" data-testid="sync-chip">
      {syncText(key)}
    </p>
  );
}

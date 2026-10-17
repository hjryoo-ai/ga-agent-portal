// 동기화 칩(지시문 ③ "동기화 지연 — 기준 {asOf.at}"): 화면의 응답들을 나쁜 쪽으로 합친다.
import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { afterEach, describe, expect, it } from 'vitest';
import { syncKey, syncText } from '../ui/SyncChip';

const clients: QueryClient[] = [];
const unsubscribes: (() => void)[] = [];

function clientWith(entries: [string, unknown, boolean][]): QueryClient {
  const client = new QueryClient();
  clients.push(client);
  for (const [key, data, active] of entries) {
    client.setQueryData([key], data);
    if (active) {
      const observer = new QueryObserver(client, { queryKey: [key], enabled: false });
      unsubscribes.push(
        observer.subscribe(() => {
          // 관찰만 한다
        }),
      );
    }
  }
  return client;
}

afterEach(() => {
  for (const u of unsubscribes.splice(0)) u();
  for (const c of clients.splice(0)) c.clear();
});

describe('sync chip', () => {
  it('shows the KST time of the smallest seq and goes delayed if any response is delayed', () => {
    const c = clientWith([
      ['me', { asOf: { seq: 11, at: '2027-02-20T01:00:00Z' }, dataStatus: 'OK' }, true],
      ['month', { asOf: { seq: 10, at: '2027-02-20T00:30:00Z' }, dataStatus: 'SYNC_DELAYED' }, true],
      ['stale', { asOf: { seq: 1, at: '2026-01-01T00:00:00Z' }, dataStatus: 'OK' }, false],
    ]);
    expect(syncText(syncKey(c))).toBe('동기화 지연 — 기준 02-20 09:30');
  });

  it('says "기준" when everything is in sync and "동기화 전" before the first cursor', () => {
    expect(syncText(syncKey(clientWith([['me', { asOf: { seq: 7, at: '2026-09-22T01:15:41Z' }, dataStatus: 'OK' }, true]])))).toBe('기준 09-22 10:15');
    expect(syncText(syncKey(clientWith([['me', { asOf: { seq: 0 }, dataStatus: 'OK' }, true]])))).toBe('동기화 전');
  });

  it('shows nothing without an active response', () => {
    expect(syncKey(clientWith([['me', { asOf: { seq: 3 }, dataStatus: 'OK' }, false]]))).toBe('');
  });
});

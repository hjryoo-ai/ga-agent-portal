// 알림함(포털 Phase 6 계획 ⑧): 최신순, "더 보기"가 다음 페이지. "모두 읽음"은 없다(지시문 — 읽음은 누른 것만).
import { useNotificationPages } from '../api/queries';
import { m } from '../messages';
import { NotificationItem } from '../notify/NotificationItem';
import { Loading } from '../ui/States';

export function NotificationsScreen() {
  const pages = useNotificationPages();
  if (pages.isPending) return <Loading />;
  if (pages.isError) return <p className="message">{m.inbox.empty}</p>;
  const items = pages.data.pages.flatMap((p) => p.items);
  return (
    <>
      <h1>{m.inbox.title}</h1>
      {items.length === 0 ? (
        <p data-testid="notifications-empty">{m.inbox.empty}</p>
      ) : (
        <ul className="notice-list" data-testid="notifications">
          {items.map((n) => (
            <NotificationItem key={n.id} notification={n} />
          ))}
        </ul>
      )}
      {pages.hasNextPage ? (
        <button type="button" className="button" data-testid="notifications-more" disabled={pages.isFetchingNextPage}
          onClick={() => {
            void pages.fetchNextPage();
          }}>
          {m.inbox.more}
        </button>
      ) : null}
    </>
  );
}

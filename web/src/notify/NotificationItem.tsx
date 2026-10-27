// 알림 한 줄(홈·알림함 공통 — 계획 ⑧): 누르면 읽음(PATCH, 안읽음일 때만) → link가 닫힌 화면 경로 넷에 맞을 때만 그리로(승인 A — 아니면 읽음만).
// 안읽음은 굵게 + 점 + 숨은 글자 "안읽음"(색·모양만으로 뜻을 싣지 않는다).
import { useNavigate } from 'react-router';
import { useMarkRead, type Notification } from '../api/queries';
import { m } from '../messages';
import { formatAt } from '../time/kst';
import { screenPath } from './links';
import { notificationText } from './text';

export function NotificationItem({ notification }: { notification: Notification }) {
  const navigate = useNavigate();
  const markRead = useMarkRead();
  const unread = notification.readAt === undefined;
  const target = screenPath('link' in notification ? notification.link : undefined);
  const open = async () => {
    if (unread) {
      try {
        await markRead.mutateAsync(notification.id);
      } catch {
        // 읽음 실패는 이동을 막지 않는다(401이면 토큰이 버려지고 로그인 화면으로 바뀐다)
      }
    }
    if (target !== null) {
      void navigate(target);
    }
  };
  return (
    <li className="notice-item">
      <button type="button" className={unread ? 'notice notice--unread' : 'notice'} data-testid="notification" data-type={notification.type}
        onClick={() => {
          void open();
        }}>
        {unread ? (
          <>
            <span className="notice__dot" aria-hidden="true" />
            <span className="visually-hidden">{m.inbox.unread} </span>
          </>
        ) : null}
        <span className="notice__text">{notificationText(notification)}</span>
        <span className="note notice__at">{formatAt(notification.createdAt)}</span>
      </button>
    </li>
  );
}

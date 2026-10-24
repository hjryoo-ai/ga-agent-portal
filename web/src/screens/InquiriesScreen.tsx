// 문의 내역(포털 Phase 6 계획 ⑧): 분류·상태 라벨·접수 시각 — 본문은 목록에 없다(계약 — Q23, 상세에만). 새 문의는 키 없이("기타").
import { Link } from 'react-router';
import { useInquiries } from '../api/queries';
import { fill, m } from '../messages';
import { formatAt } from '../time/kst';
import { Loading } from '../ui/States';

export function InquiriesScreen() {
  const list = useInquiries();
  return (
    <>
      <h1>{m.inquiry.title}</h1>
      <Link className="link-button" to="/inquiries/new" data-testid="inquiry-new">
        {m.inquiry.new}
      </Link>
      {list.isPending ? (
        <Loading />
      ) : list.isError ? null : list.data.items.length === 0 ? (
        <p data-testid="inquiries-empty">{m.inquiry.empty}</p>
      ) : (
        <ul className="items" data-testid="inquiries">
          {list.data.items.map((i) => (
            <li key={i.id} className="item">
              <Link to={`/inquiries/${i.id}`} className="item__link">
                <span>{m.inquiry.categories[i.category]}</span> <span className="badge">{m.inquiry.statuses[i.status]}</span>
              </Link>
              <p className="note">{fill(m.inquiry.createdAt, { at: formatAt(i.createdAt) })}</p>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

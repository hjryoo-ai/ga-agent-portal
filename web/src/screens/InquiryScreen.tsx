// 문의 상세(포털 Phase 6 계획 ⑧): 본문·답변(있으면)·답변 시각, 라인 키가 있으면 라인 상세 링크. 문의 본문이 화면에 나오는 곳은 여기와 작성 화면뿐이다(C7).
import { useEffect } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { useInquiry } from '../api/queries';
import { fill, m } from '../messages';
import { formatAt } from '../time/kst';
import { Loading } from '../ui/States';

export function InquiryScreen() {
  const { id = '' } = useParams();
  const detail = useInquiry(id);
  const message = detail.data?.message;
  useEffect(() => {
    if (message !== undefined) document.title = message; // S14: the body outlives this screen
  }, [message]);
  if (detail.isPending) return <Loading />;
  if (detail.isError) {
    return detail.error instanceof ApiError && detail.error.status === 404 ? <p className="message">{m.inquiry.notFound}</p> : null;
  }
  const q = detail.data;
  return (
    <>
      <h1>{m.inquiry.detailTitle}</h1>
      <section className="card" data-testid="inquiry">
        <div className="card__head">
          <h2>{m.inquiry.categories[q.category]}</h2>
          <span className="badge" data-testid="inquiry-status">
            {m.inquiry.statuses[q.status]}
          </span>
        </div>
        <p className="note">{fill(m.inquiry.createdAt, { at: formatAt(q.createdAt) })}</p>
        <p className="prewrap" data-testid="inquiry-body">
          {q.message}
        </p>
        {q.calcId === undefined ? null : (
          <Link className="link-button" to={`/lines/${encodeURIComponent(q.calcId)}`} data-testid="inquiry-line">
            {m.inquiry.toLine}
          </Link>
        )}
      </section>
      <section className="card" data-testid="inquiry-answer">
        <h2>{m.inquiry.answer}</h2>
        {q.answer === undefined ? (
          <p>{m.inquiry.noAnswer}</p>
        ) : (
          <>
            {q.answeredAt === undefined ? null : <p className="note">{fill(m.inquiry.answeredAt, { at: formatAt(q.answeredAt) })}</p>}
            <p className="prewrap">{q.answer}</p>
          </>
        )}
      </section>
    </>
  );
}

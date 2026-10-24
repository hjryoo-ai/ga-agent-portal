// 문의 작성(포털 Phase 6 계획 ⑧·Q24): /inquiries/new[?calcId=] — 라인 상세에서 오면 그 라인 키(자기 라인 키 — 주소 규약 안), 월 명세에서 오면 키 없이
// "기타"만. 분류는 누름 버튼 묶음(Choices), 본문은 계약 규칙 파일로 미리 막고(코드 포인트 — Q27), 남은 글자 수는 aria-live. 보내면 상세로 바꿔 끼운다
// (replace). 429는 Retry-After 문자열 그대로, 입력은 메모리에 그대로 둔다. 고정 문구: 고객 개인정보를 쓰지 않는다.
import { useId, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { InquiryError, useCreateInquiry, type InquiryCategory } from '../api/queries';
import { fill, m } from '../messages';
import { check, codePoints, maxLength } from '../sim/rules';
import { Choices } from '../ui/Choices';

const WITH_LINE: readonly InquiryCategory[] = ['AMOUNT', 'LIMIT', 'CLAWBACK', 'DEFERRAL', 'OTHER'];
const WITHOUT_LINE: readonly InquiryCategory[] = ['OTHER'];
const MAX = maxLength('InquiryRequest', 'message');

function errorText(error: unknown): string {
  if (error instanceof InquiryError) {
    if (error.status === 429) return fill(m.inquiry.rateLimited, { n: error.retryAfter ?? '' });
    if (error.status === 404) return m.inquiry.lineMissing;
  }
  return m.inquiry.failed;
}

export function InquiryNewScreen() {
  const [search] = useSearchParams();
  const calcId = search.get('calcId') ?? '';
  const categories = calcId === '' ? WITHOUT_LINE : WITH_LINE;
  const [category, setCategory] = useState<InquiryCategory>(categories[0] ?? 'OTHER');
  const [message, setMessage] = useState('');
  const [touched, setTouched] = useState(false);
  const create = useCreateInquiry();
  const navigate = useNavigate();
  const fieldId = useId();
  const noticeId = useId();
  const remainingId = useId();
  const values = { category, message, ...(calcId === '' ? {} : { calcId }) };
  const problems = check('InquiryRequest', values);
  const left = MAX - codePoints(message);
  const submit = () => {
    setTouched(true);
    if (Object.keys(problems).length > 0) return;
    create.mutate(
      { category, message, ...(calcId === '' ? {} : { calcId }) },
      {
        onSuccess: (created) => {
          void navigate(`/inquiries/${created.id}`, { replace: true });
        },
      },
    );
  };
  return (
    <>
      <h1>{m.inquiry.newTitle}</h1>
      <form className="form" noValidate
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}>
        <Choices label={m.inquiry.category} testId="inquiry-category" value={category}
          options={categories.map((c) => ({ value: c, name: m.inquiry.categories[c] }))}
          onChange={(v) => {
            setCategory(v as InquiryCategory);
          }} />
        <div className="field">
          <label htmlFor={fieldId}>{m.inquiry.message}</label>
          <p className="note note--warn" id={noticeId} data-testid="inquiry-notice">
            {m.inquiry.notice}
          </p>
          <textarea id={fieldId} className="textarea" rows={6} value={message} data-testid="inquiry-message" aria-describedby={`${noticeId} ${remainingId}`}
            aria-invalid={touched && problems['message'] !== undefined}
            onChange={(e) => {
              setMessage(e.target.value);
            }} />
          <p className={left < 0 ? 'note note--danger' : 'note'} id={remainingId} aria-live="polite" data-testid="inquiry-remaining">
            {left < 0 ? fill(m.inquiry.over, { n: String(-left) }) : fill(m.inquiry.remaining, { n: String(left) })}
          </p>
          {touched && problems['message'] === 'required' ? <p className="field__error">{m.inquiry.required}</p> : null}
        </div>
        {create.isError ? (
          <p className="message" role="alert" data-testid="inquiry-error">
            {errorText(create.error)}
          </p>
        ) : null}
        <button type="submit" className="button button--primary" data-testid="inquiry-submit" disabled={create.isPending}>
          {create.isPending ? m.inquiry.submitting : m.inquiry.submit}
        </button>
      </form>
    </>
  );
}

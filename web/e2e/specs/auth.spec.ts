// 인증 화면(지시문 ④·C10): 토큰 없음·모양이 아닌 토큰·만료 토큰(서버 401) → "다시 로그인", 링크 없는 subject(/me 404) → "등록되지 않은 사용자".
import { tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';

test('401과 404 화면', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  for (const kind of ['none', 'garbage', 'expired'] as const) {
    await portal.open('/', t, kind);
    await expect(page.getByTestId('screen-relogin')).toContainText('로그인이 만료되었습니다');
    await portal.check(`relogin ${kind}`);
  }
  await portal.open('/statements', t, 'unlinked');
  await expect(page.getByTestId('screen-unregistered')).toContainText('등록되지 않은 사용자입니다');
  await portal.check('unregistered');
});

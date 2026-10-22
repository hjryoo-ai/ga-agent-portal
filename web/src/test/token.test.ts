// 토큰 부트스트랩(포털 Phase 4 계획 ⑦ — Q1): 프래그먼트에서 메모리로, 주소에서는 바로 지운다. 모양이 아니면 버린다.
import { afterEach, describe, expect, it, vi } from 'vitest';
import { bootToken, currentToken, dropToken } from '../boot/token';

const JWT = 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln';

function boot(hash: string) {
  const replaceState = vi.fn();
  bootToken({ hash, pathname: '/statements/202608', search: '?x=1' }, { replaceState });
  return replaceState;
}

afterEach(() => {
  dropToken();
});

describe('bootToken', () => {
  it('moves a token from the fragment into memory and strips the fragment', () => {
    const replaceState = boot(`#token=${JWT}`);
    expect(currentToken()).toBe(JWT);
    expect(replaceState).toHaveBeenCalledExactlyOnceWith(null, '', '/statements/202608?x=1');
  });

  it('strips a fragment without a usable token and keeps no token', () => {
    expect(boot('#token=not-a-jwt')).toHaveBeenCalledOnce();
    expect(currentToken()).toBeNull();
    expect(boot('#section-2')).toHaveBeenCalledOnce();
    expect(currentToken()).toBeNull();
  });

  it('leaves the address alone when there is no fragment', () => {
    expect(boot('')).not.toHaveBeenCalled();
    expect(currentToken()).toBeNull();
  });

  it('dropToken forgets the token', () => {
    boot(`#token=${JWT}`);
    dropToken();
    expect(currentToken()).toBeNull();
  });
});

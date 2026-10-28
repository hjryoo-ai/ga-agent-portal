# -*- coding: utf-8 -*-
"""데모 렐름 파일(docker/keycloak/realm.json)을 만든다 — 포털 Phase 8 지시문 ③, 계획 Q3·Q7·P-14·P-23.

손으로 고치지 않는다. 바꿀 것이 있으면 이 스크립트를 고치고 다시 돌린다(같은 입력 → 같은 바이트):

    python3 -I docker/keycloak/generate-realm.py --password-file=<평문 한 줄 파일> > docker/keycloak/realm.json

- 데모 설계사 30명(A-2001~A-2030 — 엔진 데모 스트림의 설계사). 사용자 id(= 토큰 sub)는 고정값이고
  docker/postgres/seed-demo.sql의 identity_link.subject와 짝이다(DemoComposeFilesTest가 대조한다).
- 비밀번호는 README의 허구값 하나(전원 같음). 렐름 파일에는 평문이 아니라 PBKDF2-SHA256 해시만 둔다(계획 Q7) —
  평문은 인자·환경변수가 아니라 파일로 받는다. 소금은 사용자 id에서 만든다(재생성해도 같은 바이트).
- 클라이언트 둘: portal-web(공개, PKCE S256 필수, 표준 흐름만, 리다이렉트 = http://localhost:8080/auth/callback 하나),
  k6(공개, Direct Access Grants만 — 부하 측정 전용). 둘 다 audience 매퍼로 aud ∋ portal-demo(tenant.oidc_audience).
- 토큰 수명(계획 Q3 예시값): 액세스 300초, SSO 유휴 1,800초·최대 8시간, refresh 회전(revokeRefreshToken, 재사용 0).
"""
import argparse
import base64
import hashlib
import json
import sys

ITERATIONS = 27500
AUDIENCE = 'portal-demo'
WEB_ORIGIN = 'http://localhost:8080'


def user_id(n):
    return '00000000-0000-4000-8000-0000000%05d' % n


def credential(password, uid):
    salt = hashlib.sha256(('ga-portal-demo-salt:' + uid).encode('ascii')).digest()[:16]
    value = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, ITERATIONS, dklen=32)
    return {
        'type': 'password',
        'userLabel': 'demo',
        'secretData': json.dumps({'value': base64.b64encode(value).decode('ascii'),
                                  'salt': base64.b64encode(salt).decode('ascii'),
                                  'additionalParameters': {}}, separators=(',', ':')),
        'credentialData': json.dumps({'hashIterations': ITERATIONS, 'algorithm': 'pbkdf2-sha256',
                                      'additionalParameters': {}}, separators=(',', ':')),
    }


def audience_mapper():
    return {
        'name': 'portal-audience',
        'protocol': 'openid-connect',
        'protocolMapper': 'oidc-audience-mapper',
        'consentRequired': False,
        'config': {'included.custom.audience': AUDIENCE, 'access.token.claim': 'true', 'id.token.claim': 'false',
                   'introspection.token.claim': 'true', 'lightweight.claim': 'false'},
    }


def realm(password):
    users = []
    for n in range(2001, 2031):
        uid = user_id(n)
        users.append({
            'id': uid,
            'username': 'a-%d' % n,
            'enabled': True,
            'emailVerified': True,
            'email': 'a-%d@demo.invalid' % n,
            'firstName': 'Demo',
            'lastName': 'Agent %d' % n,
            'credentials': [credential(password, uid)],
            'requiredActions': [],
        })
    return {
        'realm': 'demo',
        'displayName': 'GA portal demo (fictional)',
        'enabled': True,
        'sslRequired': 'none',
        'registrationAllowed': False,
        'resetPasswordAllowed': False,
        'rememberMe': False,
        'loginWithEmailAllowed': False,
        'bruteForceProtected': True,
        'accessTokenLifespan': 300,
        'ssoSessionIdleTimeout': 1800,
        'ssoSessionMaxLifespan': 28800,
        'revokeRefreshToken': True,
        'refreshTokenMaxReuse': 0,
        'clients': [
            {
                'clientId': 'portal-web',
                'name': 'GA portal screens',
                'enabled': True,
                'protocol': 'openid-connect',
                'publicClient': True,
                'standardFlowEnabled': True,
                'implicitFlowEnabled': False,
                'directAccessGrantsEnabled': False,
                'serviceAccountsEnabled': False,
                'frontchannelLogout': False,
                'redirectUris': [WEB_ORIGIN + '/auth/callback'],
                'webOrigins': [WEB_ORIGIN],
                'attributes': {'pkce.code.challenge.method': 'S256', 'post.logout.redirect.uris': WEB_ORIGIN + '/'},
                'protocolMappers': [audience_mapper()],
            },
            {
                'clientId': 'k6',
                'name': 'load test (demo realm only)',
                'enabled': True,
                'protocol': 'openid-connect',
                'publicClient': True,
                'standardFlowEnabled': False,
                'implicitFlowEnabled': False,
                'directAccessGrantsEnabled': True,
                'serviceAccountsEnabled': False,
                'redirectUris': [],
                'webOrigins': [],
                'protocolMappers': [audience_mapper()],
            },
        ],
        'users': users,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--password-file', required=True)
    args = parser.parse_args()
    with open(args.password_file, encoding='utf-8') as f:
        password = f.read().strip()
    if not password:
        sys.exit('empty password file')
    json.dump(realm(password), sys.stdout, ensure_ascii=False, indent=2, sort_keys=True)
    sys.stdout.write('\n')


if __name__ == '__main__':
    main()

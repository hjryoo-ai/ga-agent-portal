package com.ga.portal.query.port;

import com.ga.portal.domain.view.SyncSnapshot;

/** 바인딩된 테넌트의 설정과 커서. 테넌트 행이 없으면 예외(인증이 발급자 표로 테넌트를 찾았으니 정상 경로에는 없다). */
public interface SyncStatusStore {

    SyncSnapshot snapshot();
}

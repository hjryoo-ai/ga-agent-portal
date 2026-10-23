package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IdentityLinkRecord;
import com.ga.portal.domain.LinkStatus;
import com.ga.portal.infra.identity.IdentityLinkResolver;
import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.NotFound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

/**
 * 컨트롤러 인자 {@link Caller}(계획 ③): 바인딩된 테넌트의 {@code identity_link}를 subject로 한 번 읽어(읽기 전용 트랜잭션) 만든다. 설계사·역할은 이 행에서만 —
 * 토큰 클레임에서 오는 것은 없다. 링크 없음·{@code REVOKED}·{@code OPERATOR}(설계사 없음 — CLI 주체)는 {@link NotFound}(사유 {@code NO_LINK}·{@code REVOKED}·
 * {@code ROLE}); 그 밖의 역할 판정({@code MANAGER} 거부)은 유스케이스가 한다(계획 승인 Q8).
 */
final class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    private final IdentityLinkResolver links;
    private final ApiTransactions transactions;

    CallerArgumentResolver(IdentityLinkResolver links, ApiTransactions transactions) {
        this.links = links;
        this.transactions = transactions;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class;
    }

    @Override
    public Caller resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest webRequest, WebDataBinderFactory binders) {
        ApiAuthentication auth = authentication(webRequest.getNativeRequest(HttpServletRequest.class));
        TenantId tenant = TenantContext.current();
        if (!tenant.equals(auth.tenant())) {
            throw new IllegalStateException("bound tenant differs from the authenticated tenant");
        }
        Optional<IdentityLinkRecord> link = transactions.read(() -> links.link(auth.subject()));
        IdentityLinkRecord record = link.orElseThrow(() -> new NotFound(DenyReason.NO_LINK));
        if (record.status() != LinkStatus.ACTIVE) {
            throw new NotFound(DenyReason.REVOKED);
        }
        return record.agentId().map(agent -> new Caller(tenant, agent, record.role()))
                .orElseThrow(() -> new NotFound(DenyReason.ROLE));
    }

    static ApiAuthentication authentication(HttpServletRequest request) {
        if (request != null && request.getAttribute(TenantBindingFilter.ATTRIBUTE) instanceof ApiAuthentication auth) {
            return auth;
        }
        throw new IllegalStateException("no authenticated API request is bound");
    }
}

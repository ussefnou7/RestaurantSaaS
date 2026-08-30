package com.smart.restaurant_saas.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Supplies {@link CurrentTenantId} parameters from {@link CurrentTenantProvider}.
 *
 * <p>Deliberately reads nothing from {@code request}: the effective tenant comes from the
 * security context, so the resolver cannot be influenced by the caller.
 */
@Component
@RequiredArgsConstructor
public class CurrentTenantIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final CurrentTenantProvider currentTenantProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentTenantId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        return currentTenantProvider.getCurrentTenantId();
    }
}

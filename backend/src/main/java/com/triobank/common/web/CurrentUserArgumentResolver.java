package com.triobank.common.web;

import com.triobank.auth.JwtService;
import com.triobank.auth.Role;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Fills {@code @CurrentUser AuthenticatedUser} parameters from the JWT of the request.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType() == AuthenticatedUser.class;
    }

    @Override
    public AuthenticatedUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                             NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            // only happens if an endpoint using @CurrentUser was made public by mistake
            throw new AuthenticationCredentialsNotFoundException("No access token in the request");
        }
        return fromJwt(token.getToken());
    }

    static AuthenticatedUser fromJwt(Jwt jwt) {
        String customerId = jwt.getClaimAsString(JwtService.CUSTOMER_ID_CLAIM);
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                Role.valueOf(jwt.getClaimAsString(JwtService.ROLE_CLAIM)),
                customerId == null ? null : UUID.fromString(customerId));
    }

}

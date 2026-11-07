package com.triobank.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Security filters reject requests before any controller runs, so GlobalExceptionHandler never sees
 * those errors. This hands them over to it, which gives 401 and 403 the same Problem Details body as
 * every other error.
 */
class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityProblemHandler.class);

    private final HandlerExceptionResolver resolver;

    SecurityProblemHandler(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        // the message says what was wrong with the token, never the token itself
        log.debug("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        resolve(request, response, ex);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        resolve(request, response, ex);
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
        if (resolver.resolveException(request, response, null, ex) == null) {
            // should not happen while GlobalExceptionHandler handles both exceptions
            response.sendError(ex instanceof AccessDeniedException
                    ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

}

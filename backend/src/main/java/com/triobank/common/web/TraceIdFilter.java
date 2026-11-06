package com.triobank.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives each request a random id. It goes to the {@code X-Trace-Id} header, to the logs and to error bodies,
 * so an error seen by a user can be found in the logs. Runs before security, so 401/403 get it too.
 * An {@code X-Trace-Id} sent by the client is ignored.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = newTraceId();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Id of the current request, or {@code null} when not inside a request. */
    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    private static String newTraceId() {
        return HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong());
    }

}

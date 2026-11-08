package com.triobank.common.error;

import com.triobank.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into a Problem Details response with {@code code}, {@code traceId},
 * {@code timestamp} and {@code errors}. Spring's own errors (unknown path, wrong method, ...)
 * get the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String CODE = "code";
    static final String TRACE_ID = "traceId";
    static final String TIMESTAMP = "timestamp";
    static final String ERRORS = "errors";

    private static final String VALIDATION_DETAIL = "One or more fields are invalid.";
    private static final String MALFORMED_DETAIL = "Request body is missing or is not valid JSON.";
    private static final String CONCURRENT_DETAIL = "The resource was modified by another request. Retry the operation.";
    private static final String UNAUTHENTICATED_DETAIL = "A valid access token is required.";
    private static final String ACCESS_DENIED_DETAIL = "You are not allowed to perform this operation.";
    private static final String INTERNAL_DETAIL = "An unexpected error occurred.";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("API error {}", ex.getCode());
        ProblemDetail body = problem(ex.getCode(), ex.getCode().status(), ex.getMessage(), request.getRequestURI());
        ex.getProperties().forEach(body::setProperty);
        return ResponseEntity.status(body.getStatus()).body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                       HttpServletRequest request) {
        log.warn("Optimistic lock conflict: {}", ex.getClass().getSimpleName());
        return respond(ErrorCode.CONCURRENT_MODIFICATION, CONCURRENT_DETAIL, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                      HttpServletRequest request) {
        String constraint = constraintName(ex);
        return ConstraintErrorCodes.forConstraint(constraint)
                .map(code -> {
                    log.warn("Constraint {} violated, mapped to {}", constraint, code);
                    return respond(code, code.title(), request);
                })
                .orElseGet(() -> {
                    log.error("Unmapped data integrity violation (constraint {})", constraint, ex);
                    return respond(ErrorCode.INTERNAL_ERROR, INTERNAL_DETAIL, request);
                });
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_DETAIL, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.debug("Access denied to {}", request.getRequestURI());
        return respond(ErrorCode.ACCESS_DENIED, ACCESS_DENIED_DETAIL, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, INTERNAL_DETAIL, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.status(),
                VALIDATION_DETAIL, path(request));
        body.setProperty(ERRORS, FieldErrorMapper.fromBindingResult(ex.getBindingResult()));
        return handleExceptionInternal(ex, body, headers, ErrorCode.VALIDATION_ERROR.status(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.status(),
                VALIDATION_DETAIL, path(request));
        body.setProperty(ERRORS, FieldErrorMapper.fromMethodValidation(ex));
        return handleExceptionInternal(ex, body, headers, ErrorCode.VALIDATION_ERROR.status(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        // Not logging the message: it can contain parts of the body, e.g. a password.
        log.debug("Malformed request body on {}", path(request));
        ProblemDetail body = problem(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.status(),
                MALFORMED_DETAIL, path(request));
        return handleExceptionInternal(ex, body, headers, ErrorCode.MALFORMED_REQUEST.status(), request);
    }

    /** All Spring MVC errors end up here; replace Spring's body with ours. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        // For most of its exceptions Spring passes null here and builds the body later.
        ProblemDetail springBody = body instanceof ProblemDetail detail ? detail
                : body == null && ex instanceof ErrorResponse errorResponse ? errorResponse.getBody()
                : null;
        if (springBody != null && !hasCode(springBody)) {
            ErrorCode code = codeForStatus(status);
            if (code == ErrorCode.INTERNAL_ERROR) {
                log.error("Spring MVC error on {}", path(request), ex);
            }
            String text = code == ErrorCode.INTERNAL_ERROR ? INTERNAL_DETAIL : springBody.getDetail();
            body = problem(code, status, text, path(request));
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail body = problem(code, code.status(), detail, request.getRequestURI());
        return ResponseEntity.status(code.status()).body(body);
    }

    private ProblemDetail problem(ErrorCode code, HttpStatusCode status, String detail, String path) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(code.type());
        body.setTitle(code.title());
        if (path != null) {
            body.setInstance(URI.create(path));
        }
        body.setProperty(CODE, code.name());
        body.setProperty(TRACE_ID, TraceIdFilter.currentTraceId());
        body.setProperty(TIMESTAMP, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        body.setProperty(ERRORS, List.of());
        return body;
    }

    private static boolean hasCode(ProblemDetail detail) {
        return detail.getProperties() != null && detail.getProperties().containsKey(CODE);
    }

    private static ErrorCode codeForStatus(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (HttpStatus.resolve(status.value())) {
            case UNAUTHORIZED -> ErrorCode.UNAUTHENTICATED;
            case FORBIDDEN -> ErrorCode.ACCESS_DENIED;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            // 400, 405, 415 and the like: something is wrong with the request itself.
            case null, default -> ErrorCode.MALFORMED_REQUEST;
        };
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }

    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

}

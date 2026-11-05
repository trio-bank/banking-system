package com.triobank.common.error;

import com.triobank.common.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Each endpoint throws one kind of error for {@link GlobalExceptionHandlerTest}. */
@RestController
@RequestMapping("/test")
class ErrorTestController {

    static final String INTERNAL_SECRET = "internal detail that must not leak";

    record Address(@Pattern(regexp = "\\d{2}-\\d{3}", message = "Postal code must match NN-NNN.") String postalCode) {
    }

    record ValidatedRequest(
            @NotBlank String name,
            @Size(min = 3, max = 5) String nickname,
            @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Valid Address address) {
    }

    record MoneyResponse(@Money BigDecimal amount, @Money BigDecimal missing, BigDecimal interestRate) {
    }

    @GetMapping("/api-error")
    void apiError() {
        throw new ApiException(ErrorCode.USER_LOCKED, "Too many failed logins.",
                Map.of("lockedUntil", "2026-11-05T10:30:30Z"));
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @PostMapping("/validated")
    void validated(@Valid @RequestBody ValidatedRequest request) {
    }

    @GetMapping("/params")
    void params(@RequestParam @Max(100) int size) {
    }

    @GetMapping("/items/{id}")
    void item(@PathVariable UUID id) {
    }

    @GetMapping("/optimistic")
    void optimistic() {
        throw new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
    }

    @GetMapping("/integrity")
    void integrity() {
        throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
    }

    @GetMapping("/denied")
    void denied() {
        throw new AccessDeniedException("Access Denied");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException(INTERNAL_SECRET);
    }

    @GetMapping("/money")
    MoneyResponse money() {
        return new MoneyResponse(new BigDecimal("10"), null, new BigDecimal("4.0000"));
    }

}

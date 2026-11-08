package com.triobank.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Locking after failed logins, from {@code security.lockout.*}.
 *
 * @param maxAttempts wrong passwords in a row that lock the user
 * @param duration    how long the lock lasts
 */
@ConfigurationProperties("security.lockout")
public record LockoutProperties(@DefaultValue("5") int maxAttempts, @DefaultValue("PT15M") Duration duration) {

    public LockoutProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("security.lockout.max-attempts must be at least 1");
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("security.lockout.duration must be positive");
        }
    }

}

package com.triobank.auth;

import com.triobank.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Someone who can log in: a customer or an admin.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(length = 255)
    private String statusReason;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    public User(String email, String passwordHash, Role role) {
        this.email = email.toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** True while a lock after failed logins is still running. */
    public boolean isLockedAt(Instant now) {
        return status == UserStatus.LOCKED && lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** A lock whose time has passed is lifted on the next login attempt. */
    void unlockIfExpired(Instant now) {
        if (status == UserStatus.LOCKED && !isLockedAt(now)) {
            status = UserStatus.ACTIVE;
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
    }

    /**
     * Counts a wrong password and locks the user when the limit is reached.
     *
     * @return true if this attempt locked the user
     */
    boolean registerFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts < maxAttempts) {
            return false;
        }
        status = UserStatus.LOCKED;
        lockedUntil = now.plus(lockDuration);
        return true;
    }

    /**
     * @return the time of the previous login, before it is replaced with {@code now}
     */
    Instant registerSuccessfulLogin(Instant now) {
        Instant previous = lastLoginAt;
        failedLoginAttempts = 0;
        lastLoginAt = now;
        return previous;
    }

}

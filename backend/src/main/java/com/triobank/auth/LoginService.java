package com.triobank.auth;

import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.dto.CurrentUserDto;
import com.triobank.auth.dto.LoginRequest;
import com.triobank.auth.dto.LoginResponse;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.CustomerService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login with email and password. Five wrong passwords in a row lock the user for a while.
 */
@Service
public class LoginService {

    private static final String WRONG_CREDENTIALS = "Email or password is incorrect.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CustomerService customerService;
    private final AuditService auditService;
    private final LockoutProperties lockout;
    private final Clock clock;
    /**
     * Checked against when the email is unknown, so such a request takes as long as a wrong password
     * and nobody can tell from the timing which emails are registered.
     */
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                        CustomerService customerService, AuditService auditService, LockoutProperties lockout,
                        Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.customerService = customerService;
        this.auditService = auditService;
        this.lockout = lockout;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Failed attempts are saved even though an error is thrown, otherwise the counter would never grow.
     *
     * @param ipAddress where the request came from, for the audit log
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditFailure(null, email, "UNKNOWN_EMAIL", ipAddress);
            throw invalidCredentials();
        }

        User user = found.get();
        user.unlockIfExpired(now);
        if (user.isLockedAt(now)) {
            auditFailure(user, email, "USER_LOCKED", ipAddress);
            throw new ApiException(ErrorCode.USER_LOCKED,
                    "Too many failed logins. Try again later.", Map.of("lockedUntil", user.getLockedUntil()));
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            auditFailure(user, email, "USER_BLOCKED", ipAddress);
            throw new ApiException(ErrorCode.USER_BLOCKED, "Your access has been blocked. Contact the bank.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            onWrongPassword(user, now, ipAddress);
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Statuses that are not used yet (PENDING_VERIFICATION, CLOSED) can't log in either.
            auditFailure(user, email, "STATUS_" + user.getStatus(), ipAddress);
            throw invalidCredentials();
        }

        Instant previousLogin = user.registerSuccessfulLogin(now);
        UUID customerId = user.getRole() == Role.CUSTOMER ? customerService.getIdByUserId(user.getId()) : null;
        IssuedToken token = jwtService.issue(user.getId(), user.getRole(), customerId);
        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.LOGIN_SUCCEEDED)
                .actorUserId(user.getId())
                .actorEmail(email)
                .actorRole(user.getRole().name())
                .targetType("USER")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .build());

        long expiresIn = Duration.between(token.issuedAt(), token.expiresAt()).toSeconds();
        CurrentUserDto userDto = new CurrentUserDto(user.getId(), email, user.getRole(), user.getStatus(),
                previousLogin, null);
        return new LoginResponse(token.value(), LoginResponse.BEARER, expiresIn, token.expiresAt(), userDto);
    }

    private void onWrongPassword(User user, Instant now, String ipAddress) {
        boolean locked = user.registerFailedLogin(now, lockout.maxAttempts(), lockout.duration());
        auditFailure(user, user.getEmail(), "WRONG_PASSWORD", ipAddress);
        if (locked) {
            auditService.record(AuditEventCommand.builder()
                    .action(AuditAction.USER_LOCKED)
                    .actorRole("SYSTEM")
                    .targetType("USER")
                    .targetId(user.getId())
                    .ipAddress(ipAddress)
                    .details(Map.of("lockedUntil", user.getLockedUntil().toString(),
                            "failedAttempts", user.getFailedLoginAttempts()))
                    .build());
        }
    }

    private void auditFailure(User user, String email, String reason, String ipAddress) {
        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.LOGIN_FAILED)
                .actorUserId(user == null ? null : user.getId())
                .actorEmail(email)
                .actorRole(user == null ? null : user.getRole().name())
                .targetType(user == null ? null : "USER")
                .targetId(user == null ? null : user.getId())
                .ipAddress(ipAddress)
                .details(Map.of("reason", reason))
                .build());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, WRONG_CREDENTIALS);
    }

}

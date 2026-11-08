package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.dto.LoginRequest;
import com.triobank.auth.dto.LoginResponse;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.CustomerService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

class LoginServiceTest {

    private static final String EMAIL = "anna@example.com";
    private static final String PASSWORD = "Str0ng!Passw0rd";
    private static final String HASH = "user-hash";
    private static final String DUMMY_HASH = "dummy-hash";
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String IP = "203.0.113.7";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-08T12:00:00Z"));

    private User user;
    private LoginService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        user = new User(EMAIL, HASH, Role.CUSTOMER);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(customerService.getIdByUserId(user.getId())).thenReturn(CUSTOMER_ID);
        when(jwtService.issue(any(), any(), any()))
                .thenAnswer(invocation -> new IssuedToken("jwt", clock.instant(),
                        clock.instant().plus(Duration.ofMinutes(30))));
        service = new LoginService(users, passwordEncoder, jwtService, customerService, auditService,
                new LockoutProperties(5, Duration.ofMinutes(15)), clock);
    }

    @Test
    void TC07_successReturnsTokenAndPreviousLogin() {
        Instant firstLogin = clock.instant();
        LoginResponse first = service.login(new LoginRequest(" Anna@Example.com ", PASSWORD), IP);
        clock.advance(Duration.ofHours(2));

        LoginResponse second = service.login(new LoginRequest(EMAIL, PASSWORD), IP);

        assertThat(first.user().lastLoginAt()).isNull();
        assertThat(second.user().lastLoginAt()).isEqualTo(firstLogin);
        assertThat(user.getLastLoginAt()).isEqualTo(clock.instant());
        assertThat(second.accessToken()).isEqualTo("jwt");
        assertThat(second.tokenType()).isEqualTo("Bearer");
        assertThat(second.expiresIn()).isEqualTo(1800);
        assertThat(second.user().customer()).isNull();
        verify(jwtService, times(2)).issue(user.getId(), Role.CUSTOMER, CUSTOMER_ID);
        assertThat(auditActions()).containsExactly(AuditAction.LOGIN_SUCCEEDED, AuditAction.LOGIN_SUCCEEDED);
    }

    @Test
    void adminTokenHasNoCustomerId() {
        User admin = new User("admin@triobank.local", HASH, Role.ADMIN);
        when(users.findByEmail("admin@triobank.local")).thenReturn(Optional.of(admin));

        service.login(new LoginRequest("admin@triobank.local", PASSWORD), IP);

        verify(jwtService).issue(admin.getId(), Role.ADMIN, null);
        verify(customerService, never()).getIdByUserId(any());
    }

    @Test
    void TC08_unknownEmailChecksDummyHashAndFailsLikeWrongPassword() {
        ApiException unknown = loginFails("nobody@example.com", PASSWORD);
        ApiException wrong = loginFails(EMAIL, "Wr0ng!Password");

        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
        assertThat(unknown.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrong.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
        assertThat(unknown.getProperties()).isEqualTo(wrong.getProperties()).isEmpty();

        AuditEventCommand audit = audits().getFirst();
        assertThat(audit.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(audit.actorEmail()).isEqualTo("nobody@example.com");
        assertThat(audit.actorUserId()).isNull();
        assertThat(audit.details()).doesNotContainValue(PASSWORD);
    }

    @Test
    void TC09_fifthWrongPasswordLocksForFifteenMinutes() {
        for (int i = 0; i < 4; i++) {
            loginFails(EMAIL, "Wr0ng!Password");
        }
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);

        loginFails(EMAIL, "Wr0ng!Password");
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(auditActions()).endsWith(AuditAction.LOGIN_FAILED, AuditAction.USER_LOCKED);

        ApiException locked = loginFails(EMAIL, PASSWORD);
        assertThat(locked.getCode()).isEqualTo(ErrorCode.USER_LOCKED);
        assertThat(locked.getProperties()).containsEntry("lockedUntil", user.getLockedUntil());

        clock.advance(Duration.ofMinutes(15));
        LoginResponse response = service.login(new LoginRequest(EMAIL, PASSWORD), IP);

        assertThat(response.user().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void successResetsFailedAttempts() {
        loginFails(EMAIL, "Wr0ng!Password");
        loginFails(EMAIL, "Wr0ng!Password");

        service.login(new LoginRequest(EMAIL, PASSWORD), IP);

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void wrongPasswordAfterExpiredLockStartsCountingAgain() {
        for (int i = 0; i < 5; i++) {
            loginFails(EMAIL, "Wr0ng!Password");
        }
        clock.advance(Duration.ofMinutes(16));

        loginFails(EMAIL, "Wr0ng!Password");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void TC10_blockedUserCannotLogIn() throws Exception {
        setStatus(user, UserStatus.BLOCKED);

        ApiException blocked = loginFails(EMAIL, PASSWORD);

        assertThat(blocked.getCode()).isEqualTo(ErrorCode.USER_BLOCKED);
        verify(jwtService, never()).issue(any(), any(), any());
        assertThat(user.getLastLoginAt()).isNull();
    }

    @Test
    void failedAttemptsAreNotRolledBack() throws Exception {
        Transactional tx = LoginService.class
                .getMethod("login", LoginRequest.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(tx.noRollbackFor()).contains(ApiException.class);
    }

    private ApiException loginFails(String email, String password) {
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.login(new LoginRequest(email, password), IP));
        assertThat(ex).isNotNull();
        return ex;
    }

    private List<AuditEventCommand> audits() {
        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService, atLeastOnce()).record(captor.capture());
        return captor.getAllValues();
    }

    private List<AuditAction> auditActions() {
        return audits().stream().map(AuditEventCommand::action).toList();
    }

    private static void setStatus(User user, UserStatus status) throws Exception {
        Field field = User.class.getDeclaredField("status");
        field.setAccessible(true);
        field.set(user, status);
    }

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

    }

}

package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapServiceTest {

    private static final String PASSWORD = "Adm1n!Passw0rd";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService auditService = mock(AuditService.class);

    @Test
    void createsAdminWithHashedPasswordAndAudits() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = service(" Admin@TrioBank.Local ", PASSWORD).bootstrap();

        assertThat(created).isTrue();
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("admin@triobank.local");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<AuditEventCommand> audit = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.ADMIN_BOOTSTRAPPED);
        assertThat(audit.getValue().actorRole()).isEqualTo("SYSTEM");
        assertThat(audit.getValue().targetType()).isEqualTo("USER");
        assertThat(audit.getValue().targetId()).isEqualTo(user.getValue().getId());
        assertThat(audit.getValue().details()).doesNotContainValue(PASSWORD).doesNotContainValue("hashed");
    }

    @Test
    void doesNothingWhenAdminExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        assertThat(service("admin@triobank.local", PASSWORD).bootstrap()).isFalse();

        verify(users, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void doesNothingWhenNotConfigured() {
        assertThat(service("", "").bootstrap()).isFalse();
        assertThat(service(null, null).bootstrap()).isFalse();

        verify(users, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void weakPasswordStopsStartup() {
        assertThatThrownBy(() -> service("admin@triobank.local", "short").bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("short");
        verify(users, never()).save(any());
    }

    @Test
    void emailOfExistingCustomerStopsStartup() {
        when(users.existsByEmail("anna@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service("anna@example.com", PASSWORD).bootstrap())
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void passwordIsNotPrinted() {
        assertThat(new AdminBootstrapProperties("admin@triobank.local", PASSWORD).toString())
                .doesNotContain(PASSWORD);
    }

    private AdminBootstrapService service(String email, String password) {
        return new AdminBootstrapService(users, passwordEncoder, auditService,
                new AdminBootstrapProperties(email, password));
    }

}

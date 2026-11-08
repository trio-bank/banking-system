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
import com.triobank.auth.dto.CurrentUserDto;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.CustomerService;
import com.triobank.customer.dto.CustomerProfileDto;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CurrentUserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CurrentUserService service = new CurrentUserService(users, customerService, auditService);

    @Test
    void customerGetsProfile() {
        User user = save(new User("anna@example.com", "hash", Role.CUSTOMER));
        CustomerProfileDto profile = mock(CustomerProfileDto.class);
        when(customerService.getProfileByUserId(user.getId())).thenReturn(profile);

        CurrentUserDto me = service.getCurrentUser(user.getId());

        assertThat(me.id()).isEqualTo(user.getId());
        assertThat(me.email()).isEqualTo("anna@example.com");
        assertThat(me.role()).isEqualTo(Role.CUSTOMER);
        assertThat(me.customer()).isSameAs(profile);
    }

    @Test
    void adminHasNoProfile() {
        User admin = save(new User("admin@triobank.local", "hash", Role.ADMIN));

        CurrentUserDto me = service.getCurrentUser(admin.getId());

        assertThat(me.customer()).isNull();
        verify(customerService, never()).getProfileByUserId(any());
    }

    @Test
    void unknownUserIsUnauthenticated() {
        assertThatThrownBy(() -> service.getCurrentUser(UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.logout(UUID.randomUUID(), "203.0.113.7"))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(auditService);
    }

    @Test
    void logoutIsAudited() {
        User user = save(new User("anna@example.com", "hash", Role.CUSTOMER));

        service.logout(user.getId(), "203.0.113.7");

        ArgumentCaptor<AuditEventCommand> audit = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOGOUT);
        assertThat(audit.getValue().actorUserId()).isEqualTo(user.getId());
        assertThat(audit.getValue().actorEmail()).isEqualTo("anna@example.com");
        assertThat(audit.getValue().ipAddress()).isEqualTo("203.0.113.7");
    }

    private User save(User user) {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

}

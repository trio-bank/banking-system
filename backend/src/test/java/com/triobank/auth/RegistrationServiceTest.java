package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.triobank.account.AccountService;
import com.triobank.account.dto.AccountDto;
import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.dto.RegisterRequest;
import com.triobank.auth.dto.RegistrationResponse;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.Customer;
import com.triobank.customer.CustomerService;
import com.triobank.customer.NewCustomer;
import com.triobank.customer.dto.AddressDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";
    private static final String PESEL = "90051412343";
    private static final String IP = "203.0.113.7";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final AccountService accountService = mock(AccountService.class);
    private final AuditService auditService = mock(AuditService.class);

    private final RegistrationService service =
            new RegistrationService(users, passwordEncoder, customerService, accountService, auditService);

    @Test
    void createsCustomerUserWithAccountAndAudits() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(UUID.randomUUID());
        when(customer.getCustomerNumber()).thenReturn("10000001");
        when(customerService.create(any(), any())).thenReturn(customer);
        AccountDto account = mock(AccountDto.class);
        when(accountService.openFirstPersonalAccount(customer, IP)).thenReturn(account);

        RegistrationResponse response = service.register(request(" Anna.Nowak@Example.com "), IP);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("anna.nowak@example.com");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<NewCustomer> data = ArgumentCaptor.forClass(NewCustomer.class);
        verify(customerService).create(eq(user.getValue()), data.capture());
        assertThat(data.getValue().pesel()).isEqualTo(PESEL);

        ArgumentCaptor<AuditEventCommand> audit = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_REGISTERED);
        assertThat(audit.getValue().targetId()).isEqualTo(user.getValue().getId());
        assertThat(audit.getValue().ipAddress()).isEqualTo(IP);
        assertThat(audit.getValue().details()).doesNotContainValue(PESEL).doesNotContainValue(PASSWORD);

        assertThat(response.userId()).isEqualTo(user.getValue().getId());
        assertThat(response.email()).isEqualTo("anna.nowak@example.com");
        assertThat(response.customerNumber()).isEqualTo("10000001");
        assertThat(response.account()).isSameAs(account);
    }

    @Test
    void emailIsCheckedCaseInsensitively() {
        when(users.existsByEmail("anna.nowak@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request("ANNA.NOWAK@example.com"), IP))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
        verify(users, never()).save(any());
        verifyNoInteractions(accountService, auditService);
    }

    @Test
    void customerChecksRunBeforePasswordIsHashed() {
        doThrow(new ApiException(ErrorCode.CUSTOMER_UNDERAGE)).when(customerService).checkCanRegister(PESEL);

        assertThatThrownBy(() -> service.register(request("anna.nowak@example.com"), IP))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CUSTOMER_UNDERAGE));
        verifyNoInteractions(passwordEncoder, accountService, auditService);
        verify(users, never()).save(any());
    }

    private static RegisterRequest request(String email) {
        return new RegisterRequest(email, PASSWORD, "Anna", "Nowak", PESEL, "+48501234567",
                new AddressDto("Krakowska", "12A", "4", "45-018", "Opole", null));
    }

}

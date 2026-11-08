package com.triobank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triobank.account.dto.AccountDto;
import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.Role;
import com.triobank.auth.User;
import com.triobank.common.config.BankProperties;
import com.triobank.common.iban.IbanGenerator;
import com.triobank.customer.Customer;
import com.triobank.product.AccountProduct;
import com.triobank.product.AccountProductService;
import com.triobank.product.AccountType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountProductService productService = mock(AccountProductService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final IbanGenerator ibanGenerator =
            new IbanGenerator(new BankProperties("99900012", "PLN", ZoneId.of("Europe/Warsaw")));

    private final AccountService service = new AccountService(accounts, productService, ibanGenerator,
            auditService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void opensPersonalAccountWithZeroBalanceAndAudits() {
        AccountProduct product = mock(AccountProduct.class);
        when(product.getCode()).thenReturn("PERSONAL_STANDARD");
        when(product.getName()).thenReturn("Trio Personal Account");
        when(product.getType()).thenReturn(AccountType.PERSONAL);
        when(product.getCurrency()).thenReturn("PLN");
        when(product.getInterestRate()).thenReturn(new BigDecimal("0.0000"));
        when(productService.getActive(AccountProductService.PERSONAL_STANDARD)).thenReturn(product);
        when(accounts.nextAccountNumber()).thenReturn("1000000000000001");
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        User user = new User("anna@example.com", "hash", Role.CUSTOMER);
        Customer customer = mock(Customer.class);
        when(customer.getUser()).thenReturn(user);

        AccountDto account = service.openFirstPersonalAccount(customer, "203.0.113.7");

        assertThat(account.iban()).isEqualTo("PL45999000121000000000000001");
        assertThat(account.name()).isEqualTo("Trio Personal Account");
        assertThat(account.type()).isEqualTo(AccountType.PERSONAL);
        assertThat(account.currency()).isEqualTo("PLN");
        assertThat(account.balance()).isEqualByComparingTo("0.00");
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.accruedInterest()).isNull();
        assertThat(account.openedAt()).isEqualTo(NOW);

        ArgumentCaptor<AuditEventCommand> audit = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.ACCOUNT_OPENED);
        assertThat(audit.getValue().actorUserId()).isEqualTo(user.getId());
        assertThat(audit.getValue().targetType()).isEqualTo("ACCOUNT");
        assertThat(audit.getValue().targetId()).isEqualTo(account.id());
        assertThat(audit.getValue().details()).containsEntry("iban", "PL45999000121000000000000001");
    }

}

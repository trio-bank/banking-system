package com.triobank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    @Test
    void listsCustomerAccountsInRepositoryOrder() {
        UUID customerId = UUID.randomUUID();
        Account personal = account(AccountType.PERSONAL, "Trio Personal Account");
        Account savings = account(AccountType.SAVINGS, "Holiday savings");
        when(accounts.findAllOfCustomer(customerId)).thenReturn(List.of(personal, savings));

        List<AccountDto> found = service.listForCustomer(customerId);

        assertThat(found).extracting(AccountDto::id).containsExactly(personal.getId(), savings.getId());
        assertThat(found).extracting(AccountDto::type).containsExactly(AccountType.PERSONAL, AccountType.SAVINGS);
        assertThat(found.get(0).accruedInterest()).isNull();
        assertThat(found.get(1).accruedInterest()).isEqualByComparingTo("0");
    }

    @Test
    void customerWithoutAccountsGetsEmptyList() {
        UUID customerId = UUID.randomUUID();
        when(accounts.findAllOfCustomer(customerId)).thenReturn(List.of());

        assertThat(service.listForCustomer(customerId)).isEmpty();
    }

    @Test
    void returnsOwnAccount() {
        UUID customerId = UUID.randomUUID();
        Account account = account();
        when(account.getProduct().getType()).thenReturn(AccountType.PERSONAL);
        when(accounts.findOfCustomer(account.getId(), customerId)).thenReturn(Optional.of(account));

        AccountDto found = service.getForCustomer(customerId, account.getId());

        assertThat(found.id()).isEqualTo(account.getId());
        assertThat(found.iban()).isEqualTo(account.getIban());
    }

    @Test
    void foreignOrMissingAccountIsNotFound() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(accounts.findOfCustomer(accountId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForCustomer(customerId, accountId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void postingAccountsComeBackAsSourceAndTarget() {
        Account source = account();
        Account target = account();
        when(accounts.findAllForUpdate(List.of(source.getId(), target.getId()))).thenReturn(List.of(target, source));

        PostingAccounts found = service.getForPosting(source.getId(), target.getId());

        assertThat(found.source()).isSameAs(source);
        assertThat(found.target()).isSameAs(target);
    }

    @Test
    void missingPostingAccountIsABug() {
        Account source = account();
        UUID missing = UUID.randomUUID();
        when(accounts.findAllForUpdate(List.of(source.getId(), missing))).thenReturn(List.of(source));

        assertThatThrownBy(() -> service.getForPosting(source.getId(), missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missing.toString());
    }

    private static Account account() {
        AccountProduct product = mock(AccountProduct.class);
        when(product.getCurrency()).thenReturn("PLN");
        return Account.openForCustomer(null, product, "PL45999000121000000000000001", "Trio Personal Account", NOW);
    }

    private static Account account(AccountType type, String name) {
        AccountProduct product = mock(AccountProduct.class);
        when(product.getType()).thenReturn(type);
        when(product.getCurrency()).thenReturn("PLN");
        return Account.openForCustomer(null, product, "PL45999000121000000000000001", name, NOW);
    }

}

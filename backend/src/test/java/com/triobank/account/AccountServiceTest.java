package com.triobank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.triobank.account.dto.OpenAccountRequest;
import com.triobank.customer.Customer;
import com.triobank.customer.CustomerService;
import com.triobank.product.AccountProduct;
import com.triobank.product.AccountProductService;
import com.triobank.product.AccountType;
import jakarta.persistence.EntityManager;
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
import org.mockito.InOrder;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountProductService productService = mock(AccountProductService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final IbanGenerator ibanGenerator =
            new IbanGenerator(new BankProperties("99900012", "PLN", ZoneId.of("Europe/Warsaw")));

    private final CustomerService customerService = mock(CustomerService.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final AccountService service = new AccountService(accounts, productService, customerService,
            ibanGenerator, auditService, entityManager, Clock.fixed(NOW, ZoneOffset.UTC));

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
    void opensSavingsAccountWithGivenName() {
        UUID customerId = UUID.randomUUID();
        AccountProduct savings = savingsProduct();
        User user = new User("anna@example.com", "hash", Role.CUSTOMER);
        lockReturnsCustomerOf(customerId, user);
        when(accounts.existsActiveOfType(customerId, AccountType.PERSONAL)).thenReturn(true);
        when(accounts.countNotClosed(customerId, savings.getId())).thenReturn(1L);
        stubAccountCreation();

        AccountDto account = service.open(customerId, new OpenAccountRequest("SAVINGS_STANDARD", "  Holiday  "),
                "203.0.113.7");

        assertThat(account.name()).isEqualTo("Holiday");
        assertThat(account.type()).isEqualTo(AccountType.SAVINGS);
        assertThat(account.productCode()).isEqualTo("SAVINGS_STANDARD");
        assertThat(account.balance()).isEqualByComparingTo("0.00");
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.openedAt()).isEqualTo(NOW);

        ArgumentCaptor<AuditEventCommand> audit = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.ACCOUNT_OPENED);
        assertThat(audit.getValue().actorUserId()).isEqualTo(user.getId());
        assertThat(audit.getValue().targetId()).isEqualTo(account.id());
        assertThat(audit.getValue().ipAddress()).isEqualTo("203.0.113.7");
        assertThat(audit.getValue().details()).containsEntry("productCode", "SAVINGS_STANDARD");
    }

    @Test
    void missingOrBlankNameBecomesProductName() {
        UUID customerId = UUID.randomUUID();
        savingsProduct();
        lockReturnsCustomerOf(customerId, new User("anna@example.com", "hash", Role.CUSTOMER));
        when(accounts.existsActiveOfType(customerId, AccountType.PERSONAL)).thenReturn(true);
        stubAccountCreation();

        for (String name : new String[] {null, "", "   "}) {
            AccountDto account = service.open(customerId, new OpenAccountRequest("SAVINGS_STANDARD", name), null);

            assertThat(account.name()).isEqualTo("Trio Savings Account");
        }
    }

    @Test
    void limitIsCheckedAfterTheCustomerIsLocked() {
        UUID customerId = UUID.randomUUID();
        AccountProduct savings = savingsProduct();
        lockReturnsCustomerOf(customerId, new User("anna@example.com", "hash", Role.CUSTOMER));
        when(accounts.existsActiveOfType(customerId, AccountType.PERSONAL)).thenReturn(true);
        stubAccountCreation();

        service.open(customerId, new OpenAccountRequest("SAVINGS_STANDARD", null), null);

        InOrder order = inOrder(customerService, accounts);
        order.verify(customerService).lockForAccountOpening(customerId);
        order.verify(accounts).countNotClosed(customerId, savings.getId());
    }

    @Test
    void accountOverTheLimitIsRejected() {
        UUID customerId = UUID.randomUUID();
        AccountProduct savings = savingsProduct();
        lockReturnsCustomerOf(customerId, new User("anna@example.com", "hash", Role.CUSTOMER));
        when(accounts.existsActiveOfType(customerId, AccountType.PERSONAL)).thenReturn(true);
        when(accounts.countNotClosed(customerId, savings.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.open(customerId, new OpenAccountRequest("SAVINGS_STANDARD", null), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.ACCOUNT_LIMIT_REACHED);
                    assertThat(ex.getMessage()).isEqualTo("You can have at most 2 accounts of this product.");
                });
        verify(accounts, never()).save(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void savingsWithoutActivePersonalAccountIsRejected() {
        UUID customerId = UUID.randomUUID();
        savingsProduct();
        lockReturnsCustomerOf(customerId, new User("anna@example.com", "hash", Role.CUSTOMER));
        when(accounts.existsActiveOfType(customerId, AccountType.PERSONAL)).thenReturn(false);

        assertThatThrownBy(() -> service.open(customerId, new OpenAccountRequest("SAVINGS_STANDARD", null), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PREREQUISITE_ACCOUNT_REQUIRED);
                    assertThat(ex.getMessage()).isEqualTo("You need an active personal account to open this product.");
                });
        verify(accounts, never()).save(any());
    }

    @Test
    void productWithoutPrerequisiteSkipsThatCheck() {
        UUID customerId = UUID.randomUUID();
        AccountProduct personal = mock(AccountProduct.class);
        when(personal.getId()).thenReturn(UUID.randomUUID());
        when(personal.getCode()).thenReturn("PERSONAL_STANDARD");
        when(personal.getName()).thenReturn("Trio Personal Account");
        when(personal.getType()).thenReturn(AccountType.PERSONAL);
        when(personal.getCurrency()).thenReturn("PLN");
        when(personal.getMaxAccountsPerCustomer()).thenReturn(2);
        when(productService.getActive("PERSONAL_STANDARD")).thenReturn(personal);
        lockReturnsCustomerOf(customerId, new User("anna@example.com", "hash", Role.CUSTOMER));
        when(accounts.countNotClosed(customerId, personal.getId())).thenReturn(1L);
        stubAccountCreation();

        AccountDto account = service.open(customerId, new OpenAccountRequest("PERSONAL_STANDARD", null), null);

        assertThat(account.type()).isEqualTo(AccountType.PERSONAL);
        verify(accounts, never()).existsActiveOfType(any(), any());
    }

    @Test
    void productErrorsStopBeforeLocking() {
        when(productService.getActive("NOPE"))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found."));

        assertThatThrownBy(() -> service.open(UUID.randomUUID(), new OpenAccountRequest("NOPE", null), null))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(customerService, never()).lockForAccountOpening(any());
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
    void postingAccountsAreLockedThenReadAgain() {
        Account source = account();
        Account target = account();
        when(entityManager.find(Account.class, source.getId())).thenReturn(source);
        when(entityManager.find(Account.class, target.getId())).thenReturn(target);

        PostingAccounts found = service.getForPosting(source.getId(), target.getId());

        assertThat(found.source()).isSameAs(source);
        assertThat(found.target()).isSameAs(target);
        InOrder order = inOrder(accounts, entityManager);
        order.verify(accounts).lockForUpdate(List.of(source.getId(), target.getId()));
        order.verify(entityManager).refresh(source);
        order.verify(entityManager).refresh(target);
    }

    @Test
    void missingPostingAccountIsABug() {
        Account source = account();
        UUID missing = UUID.randomUUID();
        when(entityManager.find(Account.class, source.getId())).thenReturn(source);

        assertThatThrownBy(() -> service.getForPosting(source.getId(), missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missing.toString());
    }

    private AccountProduct savingsProduct() {
        AccountProduct product = mock(AccountProduct.class);
        when(product.getId()).thenReturn(UUID.randomUUID());
        when(product.getCode()).thenReturn("SAVINGS_STANDARD");
        when(product.getName()).thenReturn("Trio Savings Account");
        when(product.getType()).thenReturn(AccountType.SAVINGS);
        when(product.getCurrency()).thenReturn("PLN");
        when(product.getInterestRate()).thenReturn(new BigDecimal("4.0000"));
        when(product.getMaxAccountsPerCustomer()).thenReturn(2);
        when(product.getPrerequisiteType()).thenReturn(AccountType.PERSONAL);
        when(productService.getActive("SAVINGS_STANDARD")).thenReturn(product);
        return product;
    }

    private void lockReturnsCustomerOf(UUID customerId, User user) {
        Customer customer = mock(Customer.class);
        when(customer.getUser()).thenReturn(user);
        when(customerService.lockForAccountOpening(customerId)).thenReturn(customer);
    }

    private void stubAccountCreation() {
        when(accounts.nextAccountNumber()).thenReturn("1000000000000001");
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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

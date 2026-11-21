package com.triobank.account;

import com.triobank.account.dto.AccountDto;
import com.triobank.account.dto.AccountMapper;
import com.triobank.account.dto.OpenAccountRequest;
import com.triobank.account.dto.RenameAccountRequest;
import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.Role;
import com.triobank.auth.User;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.common.iban.IbanGenerator;
import com.triobank.common.web.PageResponse;
import com.triobank.customer.Customer;
import com.triobank.customer.CustomerService;
import com.triobank.ledger.LedgerQueryService;
import com.triobank.ledger.dto.AccountHistoryItemDto;
import com.triobank.product.AccountProduct;
import com.triobank.product.AccountProductService;
import com.triobank.product.AccountType;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerQueryService ledgerQueryService;
    private final AccountProductService productService;
    private final CustomerService customerService;
    private final IbanGenerator ibanGenerator;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final Clock clock;

    public AccountService(AccountRepository accounts, LedgerQueryService ledgerQueryService,
                          AccountProductService productService, CustomerService customerService,
                          IbanGenerator ibanGenerator, AuditService auditService, EntityManager entityManager,
                          Clock clock) {
        this.accounts = accounts;
        this.ledgerQueryService = ledgerQueryService;
        this.productService = productService;
        this.customerService = customerService;
        this.ibanGenerator = ibanGenerator;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Opens the personal account every customer gets on registration. No limits to check here: the customer is new.
     *
     * @param ipAddress where the request came from, for the audit log
     */
    @Transactional
    public AccountDto openFirstPersonalAccount(Customer customer, String ipAddress) {
        AccountProduct product = productService.getActive(AccountProductService.PERSONAL_STANDARD);
        return AccountMapper.toDto(createAccount(customer, product, product.getName(), ipAddress));
    }

    /**
     * Opens one more account for the customer. Without a name the account gets the product name.
     *
     * @param ipAddress where the request came from, for the audit log
     * @throws ApiException RESOURCE_NOT_FOUND or PRODUCT_NOT_AVAILABLE for the product,
     *                      PREREQUISITE_ACCOUNT_REQUIRED if the product needs an active account of another type,
     *                      ACCOUNT_LIMIT_REACHED if the customer already has as many accounts of it as allowed
     */
    @Transactional
    public AccountDto open(UUID customerId, OpenAccountRequest request, String ipAddress) {
        AccountProduct product = productService.getActive(request.productCode());
        // Lock first, then count: otherwise two parallel requests could both see room for one more account.
        Customer customer = customerService.lockForAccountOpening(customerId);

        AccountType prerequisite = product.getPrerequisiteType();
        if (prerequisite != null && !accounts.existsActiveOfType(customerId, prerequisite)) {
            throw new ApiException(ErrorCode.PREREQUISITE_ACCOUNT_REQUIRED,
                    "You need an active " + prerequisite.name().toLowerCase(Locale.ROOT)
                            + " account to open this product.");
        }
        int limit = product.getMaxAccountsPerCustomer();
        if (accounts.countNotClosed(customerId, product.getId()) >= limit) {
            throw new ApiException(ErrorCode.ACCOUNT_LIMIT_REACHED,
                    "You can have at most " + limit + " accounts of this product.");
        }

        String name = request.name() != null ? request.name() : product.getName();
        return AccountMapper.toDto(createAccount(customer, product, name, ipAddress));
    }

    /**
     * The customer's accounts, blocked ones included: personal first, then by opening time.
     */
    @Transactional(readOnly = true)
    public List<AccountDto> listForCustomer(UUID customerId) {
        return accounts.findAllOfCustomer(customerId).stream()
                .map(AccountMapper::toDto)
                .toList();
    }

    /**
     * One of the customer's accounts. Someone else's account looks exactly like a missing one,
     * so the caller can't find out which ids exist.
     *
     * @throws ApiException RESOURCE_NOT_FOUND if the account is missing or not the customer's
     */
    @Transactional(readOnly = true)
    public AccountDto getForCustomer(UUID customerId, UUID accountId) {
        return accounts.findOfCustomer(accountId, customerId)
                .map(AccountMapper::toDto)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found."));
    }

    /**
     * History of one of the customer's accounts, newest first. Blocked and closed accounts have history too.
     *
     * @throws ApiException RESOURCE_NOT_FOUND if the account is missing or not the customer's
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountHistoryItemDto> getHistory(UUID customerId, UUID accountId, int page, int size) {
        if (!accounts.existsOfCustomer(accountId, customerId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found.");
        }
        return ledgerQueryService.getAccountHistory(accountId, page, size);
    }

    /**
     * Renames one of the customer's accounts. Renaming to the same name changes nothing and is not audited.
     *
     * @param ipAddress where the request came from, for the audit log
     * @throws ApiException RESOURCE_NOT_FOUND if the account is missing or not the customer's,
     *                      OPERATION_NOT_ALLOWED_FOR_ACCOUNT if the account is blocked or closed
     */
    @Transactional
    public AccountDto rename(UUID customerId, UUID accountId, RenameAccountRequest request, String ipAddress) {
        Account account = accounts.findOfCustomerWithOwner(accountId, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found."));
        if (!account.isActive()) {
            throw new ApiException(ErrorCode.OPERATION_NOT_ALLOWED_FOR_ACCOUNT,
                    "Only an active account can be renamed.");
        }

        String oldName = account.getName();
        if (oldName.equals(request.name())) {
            return AccountMapper.toDto(account);
        }
        account.rename(request.name());

        User owner = account.getCustomer().getUser();
        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.ACCOUNT_RENAMED)
                .actorUserId(owner.getId())
                .actorEmail(owner.getEmail())
                .actorRole(Role.CUSTOMER.name())
                .targetType("ACCOUNT")
                .targetId(account.getId())
                .ipAddress(ipAddress)
                .details(Map.of("oldName", oldName, "newName", request.name()))
                .build());
        return AccountMapper.toDto(account);
    }

    /**
     * One of the customer's accounts with its product and owner loaded, for services that move money.
     *
     * @throws ApiException RESOURCE_NOT_FOUND if the account is missing or not the customer's
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account getOfCustomerForOperation(UUID customerId, UUID accountId) {
        return accounts.findOfCustomerWithOwner(accountId, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found."));
    }

    /**
     * Id of one of the bank's own accounts. They are created by the migrations, so a missing one is a bug.
     */
    @Transactional(readOnly = true)
    public UUID getSystemAccountId(SystemAccountCode code) {
        return accounts.findBySystemCode(code)
                .map(Account::getId)
                .orElseThrow(() -> new IllegalStateException("System account " + code + " does not exist"));
    }

    /**
     * Loads and locks the two accounts of a posting until the end of the transaction.
     * For the ledger only; callers have already checked ownership.
     *
     * @throws IllegalArgumentException if an account does not exist
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PostingAccounts getForPosting(UUID sourceAccountId, UUID targetAccountId) {
        accounts.lockForUpdate(List.of(sourceAccountId, targetAccountId));
        return new PostingAccounts(loadLocked(sourceAccountId), loadLocked(targetAccountId));
    }

    /**
     * The caller may have read the account earlier in this transaction (to check the owner, for example).
     * Hibernate then gives back that object with the balance from before the lock, so it is read again
     * here. The row is already locked, so nobody can change it until we commit.
     */
    private Account loadLocked(UUID id) {
        Account account = entityManager.find(Account.class, id);
        if (account == null) {
            throw new IllegalArgumentException("Account " + id + " does not exist");
        }
        entityManager.refresh(account);
        return account;
    }

    private Account createAccount(Customer customer, AccountProduct product, String name, String ipAddress) {
        String iban = ibanGenerator.generate(accounts.nextAccountNumber());
        // The database keeps microseconds, so return the same value that is stored.
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Account account = accounts.save(Account.openForCustomer(customer, product, iban, name, now));

        User owner = customer.getUser();
        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.ACCOUNT_OPENED)
                .actorUserId(owner.getId())
                .actorEmail(owner.getEmail())
                .actorRole(Role.CUSTOMER.name())
                .targetType("ACCOUNT")
                .targetId(account.getId())
                .ipAddress(ipAddress)
                .details(Map.of("iban", iban, "productCode", product.getCode()))
                .build());
        return account;
    }

}

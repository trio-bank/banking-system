package com.triobank.account;

import com.triobank.account.dto.AccountDto;
import com.triobank.account.dto.AccountMapper;
import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.Role;
import com.triobank.auth.User;
import com.triobank.common.iban.IbanGenerator;
import com.triobank.customer.Customer;
import com.triobank.product.AccountProduct;
import com.triobank.product.AccountProductService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final AccountProductService productService;
    private final IbanGenerator ibanGenerator;
    private final AuditService auditService;
    private final Clock clock;

    public AccountService(AccountRepository accounts, AccountProductService productService,
                          IbanGenerator ibanGenerator, AuditService auditService, Clock clock) {
        this.accounts = accounts;
        this.productService = productService;
        this.ibanGenerator = ibanGenerator;
        this.auditService = auditService;
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
        String iban = ibanGenerator.generate(accounts.nextAccountNumber());
        // The database keeps microseconds, so return the same value that is stored.
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Account account = accounts.save(Account.openForCustomer(customer, product, iban, product.getName(), now));

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
        return AccountMapper.toDto(account);
    }

}

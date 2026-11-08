package com.triobank.auth;

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
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registration of a customer: user, customer and the first personal account are created together or not at all.
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final AuditService auditService;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder,
                               CustomerService customerService, AccountService accountService,
                               AuditService auditService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
        this.accountService = accountService;
        this.auditService = auditService;
    }

    /**
     * @param request   already validated
     * @param ipAddress where the request came from, for the audit log
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "This email is already registered.");
        }
        // Checked before hashing the password, which is slow on purpose.
        customerService.checkCanRegister(request.pesel());

        User user = users.save(new User(email, passwordEncoder.encode(request.password()), Role.CUSTOMER));
        Customer customer = customerService.create(user, new NewCustomer(request.firstName(), request.lastName(),
                request.pesel(), request.phoneNumber(), request.address()));
        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.USER_REGISTERED)
                .actorUserId(user.getId())
                .actorEmail(email)
                .actorRole(Role.CUSTOMER.name())
                .targetType("USER")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .details(Map.of("customerId", customer.getId(), "customerNumber", customer.getCustomerNumber()))
                .build());
        AccountDto account = accountService.openFirstPersonalAccount(customer, ipAddress);

        return new RegistrationResponse(user.getId(), customer.getCustomerNumber(), email, account);
    }

}

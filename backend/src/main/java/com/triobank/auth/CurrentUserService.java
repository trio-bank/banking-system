package com.triobank.auth;

import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.dto.CurrentUserDto;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.CustomerService;
import com.triobank.customer.dto.CustomerProfileDto;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Things a logged-in user does about their own session: look at their profile and log out.
 */
@Service
public class CurrentUserService {

    private final UserRepository users;
    private final CustomerService customerService;
    private final AuditService auditService;

    public CurrentUserService(UserRepository users, CustomerService customerService, AuditService auditService) {
        this.users = users;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    /**
     * Customers get their profile with a masked PESEL; admins get {@code customer = null}.
     */
    @Transactional(readOnly = true)
    public CurrentUserDto getCurrentUser(UUID userId) {
        User user = find(userId);
        CustomerProfileDto customer = user.getRole() == Role.CUSTOMER
                ? customerService.getProfileByUserId(userId)
                : null;
        return new CurrentUserDto(user.getId(), user.getEmail(), user.getRole(), user.getStatus(),
                user.getLastLoginAt(), customer);
    }

    /**
     * Only writes the audit event. Tokens are stateless, so the token itself stays valid until it expires;
     * the client throws it away.
     *
     * @param ipAddress where the request came from, for the audit log
     */
    @Transactional
    public void logout(UUID userId, String ipAddress) {
        User user = find(userId);
        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.LOGOUT)
                .actorUserId(user.getId())
                .actorEmail(user.getEmail())
                .actorRole(user.getRole().name())
                .targetType("USER")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .build());
    }

    /** A valid token for a user that does not exist can only come from another database, e.g. after a reset. */
    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED,
                "A valid access token is required."));
    }

}

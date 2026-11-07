package com.triobank.auth;

import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins can't register themselves, so the first one is created on startup from the environment.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapService(UserRepository users, PasswordEncoder passwordEncoder, AuditService auditService,
                                 AdminBootstrapProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Creates the admin if there is no admin yet; does nothing otherwise, so it is safe on every start.
     *
     * @return true if an admin was created
     * @throws IllegalStateException if the configured admin can't be created; the app should not start then
     */
    @Transactional
    public boolean bootstrap() {
        if (users.existsByRole(Role.ADMIN)) {
            log.debug("Admin already exists, skipping bootstrap");
            return false;
        }
        if (!properties.isSet()) {
            log.warn("No admin exists and ADMIN_EMAIL / ADMIN_PASSWORD are not set, so nobody can use the admin panel");
            return false;
        }

        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (!PasswordPolicy.isStrong(properties.password(), email)) {
            throw new IllegalStateException("ADMIN_PASSWORD does not meet the password policy");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("ADMIN_EMAIL already belongs to another user");
        }

        User admin = users.save(new User(email, passwordEncoder.encode(properties.password()), Role.ADMIN));
        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.ADMIN_BOOTSTRAPPED)
                .actorRole("SYSTEM")
                .targetType("USER")
                .targetId(admin.getId())
                .details(Map.of("email", email))
                .build());
        log.info("Created admin {}", email);
        return true;
    }

}

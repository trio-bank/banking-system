package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.triobank.IntegrationTest;
import com.triobank.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Runs in a transaction that is rolled back, so the admin does not leak into other tests. */
@IntegrationTest
@Transactional
class AdminBootstrapIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void testProfileStartsWithoutAdmin() {
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    void createsExactlyOneAdminAcrossRestarts() {
        AdminBootstrapService firstStart = service("admin@triobank.local");
        AdminBootstrapService secondStart = service("admin@triobank.local");
        AdminBootstrapService startWithOtherEmail = service("other.admin@triobank.local");

        assertThat(firstStart.bootstrap()).isTrue();
        assertThat(secondStart.bootstrap()).isFalse();
        assertThat(startWithOtherEmail.bootstrap()).isFalse();

        assertThat(count("select count(*) from users where role = 'ADMIN'")).isEqualTo(1);
        assertThat(count("select count(*) from audit_events where action = 'ADMIN_BOOTSTRAPPED'")).isEqualTo(1);
        User admin = users.findByEmail("admin@triobank.local").orElseThrow();
        assertThat(passwordEncoder.matches("Adm1n!Passw0rd", admin.getPasswordHash())).isTrue();
    }

    private AdminBootstrapService service(String email) {
        return new AdminBootstrapService(users, passwordEncoder, auditService,
                new AdminBootstrapProperties(email, "Adm1n!Passw0rd"));
    }

    private Integer count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

}

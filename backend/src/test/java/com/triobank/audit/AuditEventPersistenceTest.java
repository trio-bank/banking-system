package com.triobank.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.triobank.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit events are written to the real table, with details as JSONB.
 */
@IntegrationTest
@Transactional
class AuditEventPersistenceTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void eventWithJsonDetailsIsStoredAndLoaded() {
        UUID targetId = UUID.randomUUID();

        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.TRANSACTION_POSTED)
                .actorRole("SYSTEM")
                .targetType("TRANSACTION")
                .targetId(targetId)
                .details(Map.of("amount", "100.00", "reference", "TB202611070000000001"))
                .build());
        entityManager.flush();
        entityManager.clear();

        List<AuditEvent> events = repository.findAll().stream()
                .filter(event -> targetId.equals(event.getTargetId()))
                .toList();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getAction()).isEqualTo(AuditAction.TRANSACTION_POSTED);
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getActorUserId()).isNull();
            assertThat(event.isNew()).isFalse();
            assertThat(event.getDetails())
                    .containsEntry("amount", "100.00")
                    .containsEntry("reference", "TB202611070000000001");
        });
        assertThat(jdbc.queryForObject("SELECT details ->> 'amount' FROM audit_events WHERE target_id = ?",
                String.class, targetId)).isEqualTo("100.00");
    }

}

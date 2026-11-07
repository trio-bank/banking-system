package com.triobank.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditService service = new AuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void recordsEventWithClockTimeAndAllFields() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        service.record(AuditEventCommand.builder()
                .action(AuditAction.ACCOUNT_OPENED)
                .actorUserId(userId)
                .actorEmail("anna.nowak@example.com")
                .actorRole("CUSTOMER")
                .targetType("ACCOUNT")
                .targetId(accountId)
                .ipAddress("127.0.0.1")
                .details(Map.of("productCode", "SAVINGS_STANDARD"))
                .build());

        AuditEvent event = savedEvent();
        assertThat(event.getId()).isNotNull();
        assertThat(event.isNew()).isTrue();
        assertThat(event.getOccurredAt()).isEqualTo(NOW);
        assertThat(event.getAction()).isEqualTo(AuditAction.ACCOUNT_OPENED);
        assertThat(event.getActorUserId()).isEqualTo(userId);
        assertThat(event.getActorEmail()).isEqualTo("anna.nowak@example.com");
        assertThat(event.getActorRole()).isEqualTo("CUSTOMER");
        assertThat(event.getTargetType()).isEqualTo("ACCOUNT");
        assertThat(event.getTargetId()).isEqualTo(accountId);
        assertThat(event.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(event.getDetails()).containsExactly(Map.entry("productCode", "SAVINGS_STANDARD"));
    }

    @Test
    void detailsAreCopiedSoCallerChangesDoNotLeakIn() {
        Map<String, Object> details = new HashMap<>(Map.of("reason", "fraud"));

        service.record(AuditEventCommand.builder().action(AuditAction.USER_BLOCKED).details(details).build());
        details.put("reason", "changed");

        assertThat(savedEvent().getDetails()).containsEntry("reason", "fraud");
    }

    @Test
    void emptyDetailsAreStoredAsNull() {
        service.record(AuditEventCommand.builder().action(AuditAction.LOGOUT).details(Map.of()).build());

        assertThat(savedEvent().getDetails()).isNull();
    }

    @Test
    void actionIsRequired() {
        assertThatThrownBy(() -> AuditEventCommand.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action");
    }

    private AuditEvent savedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

}

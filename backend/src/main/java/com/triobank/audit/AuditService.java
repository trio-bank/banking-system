package com.triobank.audit;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit events. Other modules call {@link #record} inside their own transaction, so the event is saved
 * only if the audited change is saved too.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditEventCommand command) {
        repository.save(AuditEvent.of(command, Instant.now(clock)));
    }

}

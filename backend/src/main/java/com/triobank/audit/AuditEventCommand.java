package com.triobank.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * What to write to the audit log. Only {@code action} is required.
 *
 * @param actorUserId null for anonymous actions (failed login of unknown email) and system jobs
 * @param actorEmail  snapshot of the actor's email; for a failed login, the email that was tried
 * @param actorRole   {@code CUSTOMER}, {@code ADMIN} or {@code SYSTEM}
 * @param targetType  {@code USER}, {@code CUSTOMER}, {@code ACCOUNT}, {@code TRANSACTION} or {@code JOB}
 * @param details     action-specific data; never passwords, tokens or full PESEL
 */
@Builder
public record AuditEventCommand(
        AuditAction action,
        UUID actorUserId,
        String actorEmail,
        String actorRole,
        String targetType,
        UUID targetId,
        String ipAddress,
        Map<String, Object> details) {

    public AuditEventCommand {
        Objects.requireNonNull(action, "action");
    }

}

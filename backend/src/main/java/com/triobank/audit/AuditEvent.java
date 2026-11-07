package com.triobank.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * One entry in the audit log. Once written, it is never changed or deleted.
 */
@Getter
@Entity
@Immutable
@Table(name = "audit_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant occurredAt;

    private UUID actorUserId;

    @Column(length = 254)
    private String actorEmail;

    @Column(length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(length = 30)
    private String targetType;

    private UUID targetId;

    @Column(length = 45)
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    /** Lets {@code save()} insert directly instead of selecting first, because the id is set by us. */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted;

    static AuditEvent of(AuditEventCommand command, Instant occurredAt) {
        AuditEvent event = new AuditEvent();
        event.id = UUID.randomUUID();
        event.occurredAt = occurredAt;
        event.actorUserId = command.actorUserId();
        event.actorEmail = command.actorEmail();
        event.actorRole = command.actorRole();
        event.action = command.action();
        event.targetType = command.targetType();
        event.targetId = command.targetId();
        event.ipAddress = command.ipAddress();
        event.details = command.details() == null || command.details().isEmpty()
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(command.details()));
        return event;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        return id.equals(((AuditEvent) other).getId());
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

}

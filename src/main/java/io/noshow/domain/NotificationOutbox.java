package io.noshow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A queued alert awaiting delivery. Created in the same transaction as the
 * status change that triggered it (M3.2); delivered by the outbox worker with
 * retries (M3.3/M3.4). This milestone (M3.1) lands the table, entity and
 * repository only.
 */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationKind kind;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationOutbox() {
        // for JPA
    }

    /** Create a new PENDING notification, due to be sent immediately. */
    public NotificationOutbox(UUID id, UUID checkId, NotificationKind kind, String recipient,
                              String subject, String body, Instant createdAt) {
        this.id = id;
        this.checkId = checkId;
        this.kind = kind;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

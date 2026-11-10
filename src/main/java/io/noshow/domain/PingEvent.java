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
 * An append-only record of a ping received at a check's ping URL.
 * This table grows fastest, so it is kept deliberately lean.
 */
@Entity
@Table(name = "ping_event")
public class PingEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PingKind kind;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "user_agent")
    private String userAgent;

    protected PingEvent() {
        // for JPA
    }

    public PingEvent(UUID id, UUID checkId, PingKind kind, Instant receivedAt,
                     String sourceIp, String userAgent) {
        this.id = id;
        this.checkId = checkId;
        this.kind = kind;
        this.receivedAt = receivedAt;
        this.sourceIp = sourceIp;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public PingKind getKind() {
        return kind;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getUserAgent() {
        return userAgent;
    }
}

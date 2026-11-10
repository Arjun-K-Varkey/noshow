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
 * An append-only record of a single status transition for a check.
 * Drives the dashboard history and, later, alerting decisions.
 */
@Entity
@Table(name = "check_event")
public class CheckEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private CheckStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private CheckStatus toStatus;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "reason")
    private String reason;

    protected CheckEvent() {
        // for JPA
    }

    public CheckEvent(UUID id, UUID checkId, CheckStatus fromStatus, CheckStatus toStatus,
                      Instant at, String reason) {
        this.id = id;
        this.checkId = checkId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.at = at;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public CheckStatus getFromStatus() {
        return fromStatus;
    }

    public CheckStatus getToStatus() {
        return toStatus;
    }

    public Instant getAt() {
        return at;
    }

    public String getReason() {
        return reason;
    }
}

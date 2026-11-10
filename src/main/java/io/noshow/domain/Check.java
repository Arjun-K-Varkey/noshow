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
 * One monitored job.
 *
 * <p>Milestone 1 models the schedule as a simple fixed interval
 * ({@code periodSeconds}) plus a grace window. Cron expressions and time
 * zones come in a later milestone; see the build brief.
 */
@Entity
@Table(name = "checks")
public class Check {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** How often the job is expected to check in, in seconds. */
    @Column(name = "period_seconds", nullable = false)
    private long periodSeconds;

    /** Extra time allowed past the period before the check is considered late. */
    @Column(name = "grace_seconds", nullable = false)
    private long graceSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckStatus status = CheckStatus.NEW;

    /** Secret token embedded in the ping URL. */
    @Column(name = "ping_token", nullable = false, unique = true)
    private String pingToken;

    @Column(name = "last_ping_at")
    private Instant lastPingAt;

    /** When the next ping is due: last success + period. */
    @Column(name = "expected_at")
    private Instant expectedAt;

    /** The hard cutoff: expected_at + grace. Past this, the check is DOWN. */
    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Check() {
        // for JPA
    }

    public Check(UUID id, String name, long periodSeconds, long graceSeconds, String pingToken,
                 Instant createdAt) {
        this.id = id;
        this.name = name;
        this.periodSeconds = periodSeconds;
        this.graceSeconds = graceSeconds;
        this.pingToken = pingToken;
        this.status = CheckStatus.NEW;
        this.createdAt = createdAt;
    }

    /**
     * Record a successful check-in: mark the check UP and push the next
     * expected time out by period + grace from now.
     */
    public void recordSuccess(Instant at) {
        this.lastPingAt = at;
        this.status = CheckStatus.UP;
        this.expectedAt = at.plusSeconds(periodSeconds);
        this.deadline = this.expectedAt.plusSeconds(graceSeconds);
    }

    /** Record a start/fail ping. In M1 this only updates the last-seen time. */
    public void recordPing(Instant at) {
        this.lastPingAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPeriodSeconds() {
        return periodSeconds;
    }

    public long getGraceSeconds() {
        return graceSeconds;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public String getPingToken() {
        return pingToken;
    }

    public Instant getLastPingAt() {
        return lastPingAt;
    }

    public Instant getExpectedAt() {
        return expectedAt;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

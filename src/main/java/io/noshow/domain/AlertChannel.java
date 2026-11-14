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
 * A destination alerts can be delivered to. {@code config} holds the
 * channel-specific detail (an email address, a Slack/webhook URL). A channel
 * is only used for delivery once {@code verified}. Fan-out across channels is
 * M4.3; this milestone (M4.1) lands the table, entity and repository.
 */
@Entity
@Table(name = "alert_channel")
public class AlertChannel {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType type;

    @Column(nullable = false)
    private String config;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertChannel() {
        // for JPA
    }

    public AlertChannel(UUID id, ChannelType type, String config, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.config = config;
        this.verified = false;
        this.createdAt = createdAt;
    }

    /** Mark this channel verified so delivery will use it. */
    public void markVerified() {
        this.verified = true;
    }

    public UUID getId() {
        return id;
    }

    public ChannelType getType() {
        return type;
    }

    public String getConfig() {
        return config;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

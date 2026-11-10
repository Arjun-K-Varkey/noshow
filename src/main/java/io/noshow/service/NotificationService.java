package io.noshow.service;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.repo.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Enqueues alerts into the outbox. Called from inside the transaction that
 * makes a status change, so an alert is queued iff the transition commits.
 * Delivery is handled asynchronously by the outbox worker (M3.3/M3.4).
 *
 * <p>Recipient is a single configured address for now
 * ({@code noshow.alerts.recipient}); per-account / per-channel targeting
 * arrives in M4. If no recipient is configured, alerting is disabled and
 * nothing is enqueued — we do not persist outbox rows that could never be
 * delivered (setting the recipient later would not repair them).
 */
@Component
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationOutboxRepository outbox;
    private final String recipient;
    private final boolean enabled;

    public NotificationService(NotificationOutboxRepository outbox,
                               @Value("${noshow.alerts.recipient:}") String recipient) {
        this.outbox = outbox;
        this.recipient = recipient;
        this.enabled = recipient != null && !recipient.isBlank();
    }

    public void enqueue(Check check, NotificationKind kind, Instant now) {
        if (!enabled) {
            log.warn("Alert ({}) for check {} not enqueued: noshow.alerts.recipient is not set",
                    kind, check.getId());
            return;
        }
        String subject = switch (kind) {
            case DOWN -> "NoShow: " + check.getName() + " is DOWN";
            case RECOVERY -> "NoShow: " + check.getName() + " recovered";
        };
        String body = switch (kind) {
            case DOWN -> check.getName() + " missed its deadline and is now DOWN.";
            case RECOVERY -> check.getName() + " checked in again and is back UP.";
        };
        outbox.save(new NotificationOutbox(UUID.randomUUID(), check.getId(), kind,
                recipient, subject, body, now));
    }
}

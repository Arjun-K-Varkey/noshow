package io.noshow.service;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.repo.NotificationOutboxRepository;
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
 * arrives in M4.
 */
@Component
public class NotificationService {

    private final NotificationOutboxRepository outbox;
    private final String recipient;

    public NotificationService(NotificationOutboxRepository outbox,
                               @Value("${noshow.alerts.recipient:}") String recipient) {
        this.outbox = outbox;
        this.recipient = recipient;
    }

    public void enqueue(Check check, NotificationKind kind, Instant now) {
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

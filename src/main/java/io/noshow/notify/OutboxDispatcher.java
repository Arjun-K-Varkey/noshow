package io.noshow.notify;

import io.noshow.domain.NotificationOutbox;
import io.noshow.domain.OutboxStatus;
import io.noshow.repo.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Delivers queued notifications. One pass picks up the PENDING rows that are
 * due, sends each via the configured {@link AlertSender}, and marks it SENT.
 *
 * <p>If no sender is configured (e.g. SMTP is not set up) the pass is a no-op
 * and rows wait. On a send failure the row is left PENDING to be retried on a
 * later pass; proper retry with backoff and a cap is M3.4.
 */
@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final NotificationOutboxRepository outbox;
    private final ObjectProvider<AlertSender> senders;
    private final Clock clock;

    public OutboxDispatcher(NotificationOutboxRepository outbox,
                            ObjectProvider<AlertSender> senders, Clock clock) {
        this.outbox = outbox;
        this.senders = senders;
        this.clock = clock;
    }

    public void deliverPending() {
        AlertSender sender = senders.getIfAvailable();
        if (sender == null) {
            return; // no delivery mechanism configured
        }
        List<NotificationOutbox> due =
                outbox.findByStatusAndNextAttemptAtLessThanEqual(OutboxStatus.PENDING, clock.instant());
        for (NotificationOutbox notification : due) {
            try {
                sender.send(notification);
                notification.markSent(clock.instant());
                outbox.save(notification);
            } catch (Exception failure) {
                // Left PENDING; retried next pass. Backoff + cap come in M3.4.
                log.warn("Failed to send notification {} (will retry): {}",
                        notification.getId(), failure.toString());
            }
        }
    }
}

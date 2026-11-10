package io.noshow.notify;

import io.noshow.domain.NotificationOutbox;
import io.noshow.domain.OutboxStatus;
import io.noshow.repo.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
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
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    public OutboxDispatcher(NotificationOutboxRepository outbox,
                            ObjectProvider<AlertSender> senders, Clock clock,
                            @Value("${noshow.outbox.max-attempts:5}") int maxAttempts,
                            @Value("${noshow.outbox.base-backoff-ms:60000}") long baseBackoffMs,
                            @Value("${noshow.outbox.max-backoff-ms:3600000}") long maxBackoffMs) {
        this.outbox = outbox;
        this.senders = senders;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
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
            } catch (Exception failure) {
                int nextAttempt = notification.getAttempts() + 1;
                if (nextAttempt >= maxAttempts) {
                    notification.recordFailure();
                    log.warn("Giving up on notification {} after {} attempts: {}",
                            notification.getId(), nextAttempt, failure.toString());
                } else {
                    Instant retryAt = clock.instant().plus(backoffFor(nextAttempt));
                    notification.recordRetry(retryAt);
                    log.warn("Failed to send notification {} (attempt {}, retry at {}): {}",
                            notification.getId(), nextAttempt, retryAt, failure.toString());
                }
            }
            outbox.save(notification);
        }
    }

    /** Exponential backoff: base * 2^(attempt-1), capped at the max. */
    private Duration backoffFor(int attempt) {
        long shift = Math.min(attempt - 1, 30); // guard against overflow
        long ms = baseBackoffMs << shift;
        if (ms <= 0 || ms > maxBackoffMs) {
            ms = maxBackoffMs;
        }
        return Duration.ofMillis(ms);
    }
}

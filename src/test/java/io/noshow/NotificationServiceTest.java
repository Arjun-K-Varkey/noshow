package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.repo.NotificationOutboxRepository;
import io.noshow.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * M3.2 review: with no recipient configured, alerting is disabled and no
 * (undeliverable) outbox row is persisted; with one set, it enqueues.
 */
class NotificationServiceTest {

    private final Check check = new Check(
            UUID.randomUUID(), "job", 60, 30, "tok", Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void noRecipientDisablesEnqueue() {
        NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
        NotificationService service = new NotificationService(outbox, "");

        service.enqueue(check, NotificationKind.DOWN, Instant.now());

        verify(outbox, never()).save(any());
    }

    @Test
    void recipientSetEnqueues() {
        NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
        NotificationService service = new NotificationService(outbox, "ops@example.com");

        service.enqueue(check, NotificationKind.DOWN, Instant.now());

        verify(outbox).save(any(NotificationOutbox.class));
    }
}

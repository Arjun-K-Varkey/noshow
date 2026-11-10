package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.domain.OutboxStatus;
import io.noshow.notify.AlertSender;
import io.noshow.notify.OutboxDispatcher;
import io.noshow.repo.NotificationOutboxRepository;
import io.noshow.service.CheckService;
import io.noshow.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.4: a transient failure is retried (after a backoff) and eventually SENT;
 * a persistent failure stops at the attempt cap as FAILED.
 */
@SpringBootTest(properties = {
        "noshow.outbox.interval-ms=3600000",
        "noshow.evaluator.interval-ms=3600000",
        "noshow.outbox.max-attempts=3",
        "noshow.outbox.base-backoff-ms=1000",
        "noshow.outbox.max-backoff-ms=5000"
})
@Testcontainers
class OutboxRetryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    static class RecordingSender implements AlertSender {
        volatile boolean fail = false;

        @Override
        public void send(NotificationOutbox notification) {
            if (fail) {
                throw new RuntimeException("simulated send failure");
            }
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock clock() {
            return MutableClock.at(Instant.parse("2026-01-01T00:00:00Z"));
        }

        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    @Autowired
    MutableClock clock;
    @Autowired
    CheckService checkService;
    @Autowired
    NotificationOutboxRepository outbox;
    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    RecordingSender sender;

    @BeforeEach
    void reset() {
        outbox.deleteAll();
        sender.fail = false;
    }

    @Test
    void transientFailureIsRetriedThenSent() {
        NotificationOutbox row = enqueuePending();

        sender.fail = true;
        dispatcher.deliverPending(); // attempt 1 fails -> retry scheduled
        NotificationOutbox afterFail = reload(row);
        assertThat(afterFail.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterFail.getAttempts()).isEqualTo(1);
        assertThat(afterFail.getNextAttemptAt()).isAfter(clock.instant());

        clock.advanceSeconds(2); // past the 1s backoff
        sender.fail = false;
        dispatcher.deliverPending(); // now succeeds

        NotificationOutbox afterSuccess = reload(row);
        assertThat(afterSuccess.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(afterSuccess.getAttempts()).isEqualTo(1);
    }

    @Test
    void persistentFailureStopsAtCapAsFailed() {
        NotificationOutbox row = enqueuePending();
        sender.fail = true;

        dispatcher.deliverPending();      // attempt 1 -> PENDING
        clock.advanceSeconds(10);
        dispatcher.deliverPending();      // attempt 2 -> PENDING
        clock.advanceSeconds(10);
        dispatcher.deliverPending();      // attempt 3 -> cap reached -> FAILED

        NotificationOutbox failed = reload(row);
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(3);

        // A FAILED row is not retried again.
        clock.advanceSeconds(10);
        dispatcher.deliverPending();
        assertThat(reload(row).getAttempts()).isEqualTo(3);
    }

    private NotificationOutbox enqueuePending() {
        Check check = checkService.create("job", 60, 30);
        return outbox.save(new NotificationOutbox(
                UUID.randomUUID(), check.getId(), NotificationKind.DOWN,
                "ops@example.com", "NoShow: job is DOWN", "job missed its deadline",
                clock.instant()));
    }

    private NotificationOutbox reload(NotificationOutbox row) {
        return outbox.findById(row.getId()).orElseThrow();
    }
}

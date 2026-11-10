package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.domain.OutboxStatus;
import io.noshow.notify.AlertSender;
import io.noshow.notify.OutboxDispatcher;
import io.noshow.repo.NotificationOutboxRepository;
import io.noshow.service.CheckService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.3: the outbox worker sends PENDING notifications via the configured
 * sender and marks them SENT; a send failure leaves the row PENDING for a
 * later pass (retry/backoff is M3.4).
 */
@SpringBootTest(properties = {
        "noshow.outbox.interval-ms=3600000",
        "noshow.evaluator.interval-ms=3600000"
})
@Testcontainers
class OutboxWorkerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** A sender that records what it was asked to send, and can be told to fail. */
    static class RecordingSender implements AlertSender {
        final List<NotificationOutbox> sent = new ArrayList<>();
        volatile boolean fail = false;

        @Override
        public void send(NotificationOutbox notification) {
            if (fail) {
                throw new RuntimeException("simulated send failure");
            }
            sent.add(notification);
        }
    }

    @TestConfiguration
    static class SenderConfig {
        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

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
        sender.sent.clear();
        sender.fail = false;
    }

    @Test
    void deliversPendingAndMarksSent() {
        NotificationOutbox row = enqueuePending();

        dispatcher.deliverPending();

        assertThat(sender.sent).hasSize(1);
        NotificationOutbox reloaded = outbox.findById(row.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(reloaded.getSentAt()).isNotNull();
    }

    @Test
    void sendFailureLeavesRowPending() {
        NotificationOutbox row = enqueuePending();
        sender.fail = true;

        dispatcher.deliverPending();

        assertThat(outbox.findById(row.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PENDING);
    }

    private NotificationOutbox enqueuePending() {
        Check check = checkService.create("job", 60, 30);
        return outbox.save(new NotificationOutbox(
                UUID.randomUUID(), check.getId(), NotificationKind.DOWN,
                "ops@example.com", "NoShow: job is DOWN", "job missed its deadline",
                Instant.parse("2020-01-01T00:00:00Z"))); // due in the past
    }
}

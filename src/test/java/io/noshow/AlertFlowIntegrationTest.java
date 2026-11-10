package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.domain.OutboxStatus;
import io.noshow.domain.PingKind;
import io.noshow.evaluator.EvaluatorService;
import io.noshow.notify.AlertSender;
import io.noshow.notify.OutboxDispatcher;
import io.noshow.repo.NotificationOutboxRepository;
import io.noshow.service.CheckService;
import io.noshow.service.PingService;
import io.noshow.support.MutableClock;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.6: the whole alert chain end to end — a check goes DOWN, which enqueues a
 * DOWN alert, which the worker then delivers and marks SENT; a later recovery
 * ping enqueues a RECOVERY alert the worker also delivers.
 */
@SpringBootTest(properties = {
        "noshow.evaluator.interval-ms=3600000",
        "noshow.outbox.interval-ms=3600000",
        "noshow.alerts.recipient=ops@example.com"
})
@Testcontainers
class AlertFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    static class RecordingSender implements AlertSender {
        final List<NotificationOutbox> sent = new ArrayList<>();

        @Override
        public void send(NotificationOutbox notification) {
            sent.add(notification);
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
    PingService pingService;
    @Autowired
    EvaluatorService evaluator;
    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    NotificationOutboxRepository outbox;
    @Autowired
    RecordingSender sender;

    @Test
    void downThenRecoveryFlowsAllTheWayToDelivery() {
        Check check = checkService.create("backup-nightly", 60, 30);
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t");

        // Go DOWN -> enqueues a DOWN alert.
        clock.advanceSeconds(200);
        evaluator.evaluateDueChecks();
        assertThat(outbox.countByCheckIdAndKind(check.getId(), NotificationKind.DOWN)).isEqualTo(1);

        // Worker delivers it.
        dispatcher.deliverPending();
        assertThat(sender.sent).extracting(NotificationOutbox::getKind).containsExactly(NotificationKind.DOWN);

        // Recover -> enqueues a RECOVERY alert, which the worker also delivers.
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t");
        dispatcher.deliverPending();
        assertThat(sender.sent).extracting(NotificationOutbox::getKind)
                .containsExactly(NotificationKind.DOWN, NotificationKind.RECOVERY);

        // Both outbox rows ended SENT.
        assertThat(outbox.findAll()).allSatisfy(row ->
                assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT));
    }
}

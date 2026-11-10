package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.PingKind;
import io.noshow.evaluator.EvaluatorService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.2: a DOWN transition enqueues one DOWN alert, a DOWN->UP recovery
 * enqueues one RECOVERY alert, and neither a soft LATE nor a LATE->UP recovery
 * enqueues anything. Repeated sweeps while DOWN do not duplicate.
 */
@SpringBootTest(properties = {
        "noshow.evaluator.interval-ms=3600000",
        "noshow.alerts.recipient=ops@example.com"
})
@Testcontainers
class NotificationEnqueueTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock clock() {
            return MutableClock.at(Instant.parse("2026-01-01T00:00:00Z"));
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
    NotificationOutboxRepository outbox;

    @Test
    void downEnqueuesOneDownAlertAndNoDuplicates() {
        Check check = makeDownCheck();
        assertThat(outbox.countByCheckIdAndKind(check.getId(), NotificationKind.DOWN)).isEqualTo(1);

        evaluator.evaluateDueChecks(); // DOWN is not re-selected
        evaluator.evaluateDueChecks();
        assertThat(outbox.countByCheckIdAndKind(check.getId(), NotificationKind.DOWN)).isEqualTo(1);
    }

    @Test
    void recoveryFromDownEnqueuesOneRecoveryAlert() {
        Check check = makeDownCheck();
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t"); // DOWN -> UP
        assertThat(outbox.countByCheckIdAndKind(check.getId(), NotificationKind.RECOVERY)).isEqualTo(1);
    }

    @Test
    void lateAndLateRecoveryEnqueueNothing() {
        Check check = checkService.create("job", 60, 30); // expected +60, deadline +90
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t");

        clock.advanceSeconds(70); // within grace -> LATE, no DOWN alert
        evaluator.evaluateDueChecks();
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t"); // LATE -> UP

        assertThat(outbox.countByCheckId(check.getId())).isZero();
    }

    /** Create a check, ping it UP, then advance past the deadline and sweep it DOWN. */
    private Check makeDownCheck() {
        Check check = checkService.create("job", 60, 30);
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t");
        clock.advanceSeconds(200);
        evaluator.evaluateDueChecks();
        return check;
    }
}

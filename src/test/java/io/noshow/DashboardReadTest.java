package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.CheckEvent;
import io.noshow.domain.CheckStatus;
import io.noshow.domain.PingKind;
import io.noshow.evaluator.EvaluatorService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2.5: the dashboard read model — per-check status history newest-first and
 * the last-transition time.
 */
@SpringBootTest(properties = "noshow.evaluator.interval-ms=3600000")
@Testcontainers
class DashboardReadTest {

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

    @Test
    void historyIsNewestFirstAndLastTransitionIsTheLatest() {
        Check check = checkService.create("job", 60, 30); // expected +60, deadline +90

        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t"); // NEW -> UP @0
        clock.advanceSeconds(70);
        evaluator.evaluateDueChecks();                                                     // UP -> LATE @70
        clock.advanceSeconds(30);
        evaluator.evaluateDueChecks();                                                     // LATE -> DOWN @100
        clock.advanceSeconds(50);
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t"); // DOWN -> UP @150

        List<CheckEvent> history = checkService.history(check.getId());
        assertThat(history).hasSize(4);
        // newest first
        assertThat(history.get(0).getFromStatus()).isEqualTo(CheckStatus.DOWN);
        assertThat(history.get(0).getToStatus()).isEqualTo(CheckStatus.UP);
        assertThat(history.get(3).getFromStatus()).isEqualTo(CheckStatus.NEW);
        assertThat(history.get(3).getToStatus()).isEqualTo(CheckStatus.UP);

        assertThat(checkService.lastTransitionAt(check.getId()))
                .isEqualTo(history.get(0).getAt());
    }
}

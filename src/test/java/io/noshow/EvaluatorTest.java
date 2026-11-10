package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.CheckEvent;
import io.noshow.domain.CheckStatus;
import io.noshow.domain.PingKind;
import io.noshow.evaluator.EvaluatorService;
import io.noshow.repo.CheckEventRepository;
import io.noshow.repo.CheckRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the evaluator with a controlled clock: UP -> LATE -> DOWN as time
 * advances past expected_at and then the deadline, with one check_event per
 * transition. The exhaustive cases (grace=0, paused, recovery, no-duplicates)
 * live in M2.6.
 */
@SpringBootTest(properties = "noshow.evaluator.interval-ms=3600000") // keep the real scheduler out of the test
@Testcontainers
class EvaluatorTest {

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
    CheckRepository checkRepository;

    @Autowired
    CheckEventRepository checkEventRepository;

    @Test
    void upToLateToDownAsTimeAdvances() {
        // period 60s, grace 30s -> expected at +60s, deadline at +90s
        Check check = checkService.create("job", 60, 30);
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "curl/test");
        assertThat(reload(check).getStatus()).isEqualTo(CheckStatus.UP);

        // +70s: past expected, within grace -> LATE
        clock.advanceSeconds(70);
        evaluator.evaluateDueChecks();
        assertThat(reload(check).getStatus()).isEqualTo(CheckStatus.LATE);

        // +100s total: past deadline -> DOWN
        clock.advanceSeconds(30);
        evaluator.evaluateDueChecks();
        assertThat(reload(check).getStatus()).isEqualTo(CheckStatus.DOWN);

        // A further sweep with no change records nothing new.
        evaluator.evaluateDueChecks();

        // Only the evaluator's own transitions here (the first SUCCESS ping also
        // logs a NEW -> UP event via the ping path).
        List<CheckEvent> sweepEvents = checkEventRepository.findByCheckIdOrderByAtDesc(check.getId())
                .stream().filter(e -> "evaluator".equals(e.getReason())).toList();
        assertThat(sweepEvents).hasSize(2);
        assertThat(sweepEvents.get(0).getToStatus()).isEqualTo(CheckStatus.DOWN);
        assertThat(sweepEvents.get(0).getFromStatus()).isEqualTo(CheckStatus.LATE);
        assertThat(sweepEvents.get(1).getToStatus()).isEqualTo(CheckStatus.LATE);
        assertThat(sweepEvents.get(1).getFromStatus()).isEqualTo(CheckStatus.UP);
    }

    private Check reload(Check check) {
        return checkRepository.findById(check.getId()).orElseThrow();
    }
}

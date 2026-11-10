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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2.6: the remaining state-machine cases. (UP->LATE->DOWN and DOWN->UP
 * recovery are covered by EvaluatorTest and RecoveryTest.)
 *
 * <ul>
 *   <li>grace = 0 goes straight UP -> DOWN with no LATE window,</li>
 *   <li>a PAUSED check is never touched by a sweep,</li>
 *   <li>repeated sweeps while status is unchanged record no duplicate events.</li>
 * </ul>
 */
@SpringBootTest(properties = "noshow.evaluator.interval-ms=3600000")
@Testcontainers
class StateMachineTest {

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
    void zeroGraceGoesStraightToDownWithoutLate() {
        Check check = checkService.create("job", 60, 0); // expected == deadline == +60
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t");

        clock.advanceSeconds(61); // past the (grace-free) deadline
        evaluator.evaluateDueChecks();

        assertThat(reload(check).getStatus()).isEqualTo(CheckStatus.DOWN);
        assertThat(sweepEvents(check)).hasSize(1);
        assertThat(sweepEvents(check).get(0).getFromStatus()).isEqualTo(CheckStatus.UP);
        assertThat(sweepEvents(check).get(0).getToStatus()).isEqualTo(CheckStatus.DOWN);
        // never passed through LATE
        assertThat(allEvents(check)).noneMatch(e -> e.getToStatus() == CheckStatus.LATE);
    }

    @Test
    void pausedCheckIsNeverTouchedBySweep() {
        Check check = checkService.create("job", 60, 30);
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t");

        // Force PAUSED (no pause feature yet; set it directly).
        Check loaded = reload(check);
        loaded.transitionTo(CheckStatus.PAUSED);
        checkRepository.save(loaded);

        clock.advanceSeconds(10_000); // well past any deadline
        evaluator.evaluateDueChecks();

        assertThat(reload(check).getStatus()).isEqualTo(CheckStatus.PAUSED);
        assertThat(sweepEvents(check)).isEmpty(); // evaluator produced nothing
    }

    @Test
    void repeatedSweepsWhileLateRecordNoDuplicates() {
        Check check = checkService.create("job", 60, 30); // expected +60, deadline +90
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "t");

        clock.advanceSeconds(70); // within grace -> LATE
        evaluator.evaluateDueChecks();
        evaluator.evaluateDueChecks();
        evaluator.evaluateDueChecks();

        assertThat(reload(check).getStatus()).isEqualTo(CheckStatus.LATE);
        assertThat(sweepEvents(check)).hasSize(1); // only the single UP -> LATE
        assertThat(sweepEvents(check).get(0).getToStatus()).isEqualTo(CheckStatus.LATE);
    }

    private Check reload(Check check) {
        return checkRepository.findById(check.getId()).orElseThrow();
    }

    private List<CheckEvent> allEvents(Check check) {
        return checkEventRepository.findByCheckIdOrderByAtDesc(check.getId());
    }

    private List<CheckEvent> sweepEvents(Check check) {
        return allEvents(check).stream().filter(e -> "evaluator".equals(e.getReason())).toList();
    }
}

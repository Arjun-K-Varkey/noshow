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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2.4: a success ping on a DOWN check brings it back to UP, recomputes the
 * deadline, and logs the recovery transition exactly once.
 */
@SpringBootTest(properties = "noshow.evaluator.interval-ms=3600000")
@Testcontainers
class RecoveryTest {

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
    void successPingRecoversDownCheck() {
        Check check = checkService.create("job", 60, 30); // expected +60, deadline +90
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "curl/test");

        // Let it go DOWN.
        clock.advanceSeconds(200);
        evaluator.evaluateDueChecks();
        assertThat(reload(check).getStatus()).isEqualTo(CheckStatus.DOWN);
        Instant downDeadline = reload(check).getDeadline();

        // A success ping recovers it to UP and pushes the deadline out.
        pingService.recordPing(check.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "curl/test");
        Check recovered = reload(check);
        assertThat(recovered.getStatus()).isEqualTo(CheckStatus.UP);
        assertThat(recovered.getDeadline()).isAfter(downDeadline);

        // The recovery transition is logged once, via the ping path.
        var recoveries = checkEventRepository.findByCheckIdOrderByAtDesc(check.getId())
                .stream()
                .filter(e -> e.getToStatus() == CheckStatus.UP
                        && e.getFromStatus() == CheckStatus.DOWN)
                .toList();
        assertThat(recoveries).hasSize(1);
        assertThat(recoveries.get(0).getReason()).isEqualTo("ping");
    }

    private Check reload(Check check) {
        return checkRepository.findById(check.getId()).orElseThrow();
    }
}

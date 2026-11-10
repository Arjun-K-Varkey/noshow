package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.CheckStatus;
import io.noshow.domain.PingKind;
import io.noshow.repo.CheckRepository;
import io.noshow.service.CheckService;
import io.noshow.service.PingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 1 happy path, end to end against a real Postgres:
 * create a check, send a success ping, and confirm it flips to UP with
 * its next-expected time advanced.
 */
@SpringBootTest
@Testcontainers
class PingFlowTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CheckService checkService;

    @Autowired
    PingService pingService;

    @Autowired
    CheckRepository checkRepository;

    @Test
    void successPingMarksCheckUp() {
        Check created = checkService.create("backup-nightly", 3600, 300);
        assertThat(created.getStatus()).isEqualTo(CheckStatus.NEW);
        assertThat(created.getNextExpectedAt()).isNull();

        boolean matched = pingService.recordPing(created.getPingToken(), PingKind.SUCCESS, "127.0.0.1", "curl/test");
        assertThat(matched).isTrue();

        Check reloaded = checkRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CheckStatus.UP);
        assertThat(reloaded.getLastPingAt()).isNotNull();
        assertThat(reloaded.getNextExpectedAt()).isNotNull();
        assertThat(reloaded.getNextExpectedAt()).isAfter(reloaded.getLastPingAt());
    }

    @Test
    void unknownTokenIsRejected() {
        boolean matched = pingService.recordPing("does-not-exist", PingKind.SUCCESS, "127.0.0.1", "curl/test");
        assertThat(matched).isFalse();
    }
}

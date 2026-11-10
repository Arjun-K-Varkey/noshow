package io.noshow;

import io.noshow.domain.Check;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.domain.OutboxStatus;
import io.noshow.repo.NotificationOutboxRepository;
import io.noshow.service.CheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.1: the notification_outbox table and entity persist and read back, with
 * a sane default state.
 */
@SpringBootTest
@Testcontainers
class OutboxRepositoryTest {

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
    NotificationOutboxRepository outbox;

    @Test
    void persistsAndReadsBackAsPending() {
        Check check = checkService.create("job", 60, 30);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        NotificationOutbox saved = outbox.save(new NotificationOutbox(
                UUID.randomUUID(), check.getId(), NotificationKind.DOWN,
                "ops@example.com", "NoShow: job is DOWN", "job missed its deadline", now));

        NotificationOutbox reloaded = outbox.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttempts()).isZero();
        assertThat(reloaded.getKind()).isEqualTo(NotificationKind.DOWN);
        assertThat(reloaded.getRecipient()).isEqualTo("ops@example.com");
        assertThat(reloaded.getNextAttemptAt()).isEqualTo(now);
        assertThat(reloaded.getSentAt()).isNull();
        assertThat(outbox.countByCheckId(check.getId())).isEqualTo(1);
    }
}

package io.noshow;

import io.noshow.domain.AlertChannel;
import io.noshow.domain.ChannelType;
import io.noshow.repo.AlertChannelRepository;
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
 * M4.1: the alert_channel table and entity persist and read back, default to
 * unverified, and verification flips the flag (and the verified-only query).
 */
@SpringBootTest
@Testcontainers
class AlertChannelRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AlertChannelRepository channels;

    @Test
    void persistsUnverifiedThenVerifies() {
        AlertChannel saved = channels.save(new AlertChannel(
                UUID.randomUUID(), ChannelType.SLACK,
                "https://hooks.slack.example/T000/B000/xxx", Instant.parse("2026-01-01T00:00:00Z")));

        AlertChannel reloaded = channels.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getType()).isEqualTo(ChannelType.SLACK);
        assertThat(reloaded.isVerified()).isFalse();
        assertThat(channels.findByVerifiedTrue()).noneMatch(c -> c.getId().equals(saved.getId()));

        reloaded.markVerified();
        channels.save(reloaded);
        assertThat(channels.findByVerifiedTrue()).anyMatch(c -> c.getId().equals(saved.getId()));
    }
}

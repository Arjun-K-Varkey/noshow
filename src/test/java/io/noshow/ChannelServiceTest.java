package io.noshow;

import io.noshow.domain.AlertChannel;
import io.noshow.domain.ChannelType;
import io.noshow.notify.ChannelSender;
import io.noshow.repo.AlertChannelRepository;
import io.noshow.service.ChannelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M4.2: verifying a channel sends a test through its sender and marks it
 * verified; a channel type with no sender cannot be verified and stays
 * unverified.
 */
@SpringBootTest
@Testcontainers
class ChannelServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Stands in for the real EmailChannelSender (which needs SMTP configured). */
    static class FakeEmailSender implements ChannelSender {
        final List<AlertChannel> sent = new ArrayList<>();

        @Override
        public ChannelType type() {
            return ChannelType.EMAIL;
        }

        @Override
        public void send(AlertChannel channel, String subject, String body) {
            sent.add(channel);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        FakeEmailSender fakeEmailSender() {
            return new FakeEmailSender();
        }
    }

    @Autowired
    ChannelService service;
    @Autowired
    AlertChannelRepository channels;
    @Autowired
    FakeEmailSender sender;

    @Test
    void verifyingEmailSendsTestAndMarksVerified() {
        AlertChannel channel = service.create(ChannelType.EMAIL, "ops@example.com");
        assertThat(channel.isVerified()).isFalse();

        service.verify(channel.getId());

        assertThat(sender.sent).hasSize(1);
        assertThat(channels.findById(channel.getId()).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void channelWithNoSenderCannotBeVerified() {
        AlertChannel channel = service.create(ChannelType.SLACK, "https://hooks.slack.example/x");

        assertThatThrownBy(() -> service.verify(channel.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(channels.findById(channel.getId()).orElseThrow().isVerified()).isFalse();
    }
}

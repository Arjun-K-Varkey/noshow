package io.noshow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the application {@link Clock}. Everything that needs the current
 * time injects this bean and calls {@code clock.instant()} rather than
 * {@code Instant.now()}, so tests (and later the evaluator) can drive time
 * deterministically by supplying a fixed or adjustable Clock.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

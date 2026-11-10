package io.noshow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A pooled task scheduler for @Scheduled work. Spring's default scheduler is
 * single-threaded, which would let a slow outbox delivery (a stalled SMTP
 * server) block the evaluator sweep and delay DOWN detection. A small pool
 * keeps delivery and evaluation on separate threads.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("noshow-sched-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}

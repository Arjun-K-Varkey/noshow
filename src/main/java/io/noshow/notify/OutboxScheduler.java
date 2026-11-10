package io.noshow.notify;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs an outbox delivery pass on a fixed delay. Kept separate from
 * {@link OutboxDispatcher} so the delivery logic can be invoked directly in
 * tests. Interval: {@code noshow.outbox.interval-ms} (default 15s).
 */
@Component
public class OutboxScheduler {

    private final OutboxDispatcher dispatcher;

    public OutboxScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${noshow.outbox.interval-ms:15000}")
    public void deliver() {
        dispatcher.deliverPending();
    }
}

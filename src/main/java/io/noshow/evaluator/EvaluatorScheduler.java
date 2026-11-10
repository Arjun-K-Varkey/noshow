package io.noshow.evaluator;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers an evaluator sweep on a fixed delay. Kept separate from
 * {@link EvaluatorService} so the sweep logic can be driven directly (with a
 * controlled clock) in tests, without waiting on the scheduler.
 *
 * <p>Interval is configurable via {@code noshow.evaluator.interval-ms}
 * (default 30s).
 */
@Component
public class EvaluatorScheduler {

    private final EvaluatorService evaluator;

    public EvaluatorScheduler(EvaluatorService evaluator) {
        this.evaluator = evaluator;
    }

    @Scheduled(fixedDelayString = "${noshow.evaluator.interval-ms:30000}")
    public void sweep() {
        evaluator.evaluateDueChecks();
    }
}

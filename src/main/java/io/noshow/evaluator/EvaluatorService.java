package io.noshow.evaluator;

import io.noshow.domain.Check;
import io.noshow.domain.CheckEvent;
import io.noshow.domain.CheckStatus;
import io.noshow.repo.CheckEventRepository;
import io.noshow.repo.CheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The dead man's switch. One sweep walks every active check and applies the
 * state machine against the current time:
 *
 * <pre>
 *   UP   -> LATE   when now is past expected_at but within the deadline
 *   UP   -> DOWN   when now is already past the deadline (e.g. grace = 0)
 *   LATE -> DOWN   when now is past the deadline
 * </pre>
 *
 * Recovery (LATE/DOWN -> UP on a ping) lives in PingService/M2.4. PAUSED and
 * NEW checks are never touched here. Every transition is logged to
 * {@code check_event}.
 *
 * <p>Multi-instance safety (so two app instances do not both evaluate the same
 * check) is out of scope for this milestone and handled in M5 with a database
 * lock. This sweep assumes a single evaluator instance.
 */
@Service
public class EvaluatorService {

    private static final String REASON = "evaluator";

    private final CheckRepository checks;
    private final CheckEventRepository events;
    private final Clock clock;

    public EvaluatorService(CheckRepository checks, CheckEventRepository events, Clock clock) {
        this.checks = checks;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void evaluateDueChecks() {
        Instant now = clock.instant();
        List<Check> active = checks.findByStatusIn(List.of(CheckStatus.UP, CheckStatus.LATE));
        for (Check check : active) {
            evaluate(check, now);
        }
    }

    private void evaluate(Check check, Instant now) {
        if (check.getDeadline() == null || check.getExpectedAt() == null) {
            return; // no successful ping yet; nothing to evaluate
        }
        CheckStatus from = check.getStatus();
        CheckStatus to = from;

        if (now.isAfter(check.getDeadline())) {
            to = CheckStatus.DOWN;
        } else if (now.isAfter(check.getExpectedAt())) {
            to = CheckStatus.LATE;
        }

        if (to != from) {
            check.transitionTo(to);
            checks.save(check);
            events.save(new CheckEvent(UUID.randomUUID(), check.getId(), from, to, now, REASON));
        }
    }
}

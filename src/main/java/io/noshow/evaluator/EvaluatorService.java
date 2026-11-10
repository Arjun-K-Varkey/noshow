package io.noshow.evaluator;

import io.noshow.domain.Check;
import io.noshow.domain.CheckStatus;
import io.noshow.repo.CheckRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The dead man's switch. One sweep selects every check that is due for
 * evaluation — active and already past its expected time — and applies the
 * state machine against the current time:
 *
 * <pre>
 *   UP   -> LATE   when now is past expected_at but within the deadline
 *   UP   -> DOWN   when now is already past the deadline (e.g. grace = 0)
 *   LATE -> DOWN   when now is past the deadline
 * </pre>
 *
 * Recovery (LATE/DOWN -> UP on a ping) lives in the ping path (M2.4). PAUSED
 * and NEW checks are never selected. Each check is transitioned in its own
 * transaction (see {@link CheckTransitioner}); if a concurrent success ping
 * updates a check mid-sweep, that check's optimistic-lock save fails and it is
 * skipped so the ping is never overwritten.
 *
 * <p>Multi-instance safety across nodes (so two instances don't both sweep the
 * same check) is M5, using a database lock; this guards only the in-process
 * scheduler/ping race.
 */
@Service
public class EvaluatorService {

    private static final List<CheckStatus> ACTIVE = List.of(CheckStatus.UP, CheckStatus.LATE);

    private final CheckRepository checks;
    private final CheckTransitioner transitioner;
    private final Clock clock;

    public EvaluatorService(CheckRepository checks, CheckTransitioner transitioner, Clock clock) {
        this.checks = checks;
        this.transitioner = transitioner;
        this.clock = clock;
    }

    public void evaluateDueChecks() {
        Instant now = clock.instant();
        List<Check> due = checks.findByStatusInAndExpectedAtLessThanEqual(ACTIVE, now);
        for (Check check : due) {
            try {
                transitioner.transition(check.getId(), now);
            } catch (OptimisticLockingFailureException concurrentPing) {
                // A success ping updated this check mid-sweep; the ping wins, skip.
            }
        }
    }
}

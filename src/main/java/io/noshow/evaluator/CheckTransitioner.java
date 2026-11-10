package io.noshow.evaluator;

import io.noshow.domain.Check;
import io.noshow.domain.CheckEvent;
import io.noshow.domain.CheckStatus;
import io.noshow.domain.NotificationKind;
import io.noshow.repo.CheckEventRepository;
import io.noshow.repo.CheckRepository;
import io.noshow.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies a single check's transition in its own transaction. Re-reads the
 * check inside the transaction (so it acts on the latest committed state) and
 * relies on {@code Check}'s optimistic-lock version: if a concurrent success
 * ping updates the check before this transaction commits, the save fails with
 * an optimistic-locking exception and the caller skips it — the ping wins,
 * never overwritten by a stale sweep.
 */
@Component
public class CheckTransitioner {

    private static final String REASON = "evaluator";

    private final CheckRepository checks;
    private final CheckEventRepository events;
    private final NotificationService notifications;

    public CheckTransitioner(CheckRepository checks, CheckEventRepository events,
                             NotificationService notifications) {
        this.checks = checks;
        this.events = events;
        this.notifications = notifications;
    }

    @Transactional
    public void transition(UUID checkId, Instant now) {
        Check check = checks.findById(checkId).orElse(null);
        if (check == null) {
            return;
        }
        CheckStatus from = check.getStatus();
        if (from != CheckStatus.UP && from != CheckStatus.LATE) {
            return; // changed since it was listed (e.g. a ping, or paused)
        }
        if (check.getDeadline() == null || check.getExpectedAt() == null) {
            return;
        }

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
            if (to == CheckStatus.DOWN) {
                notifications.enqueue(check, NotificationKind.DOWN, now);
            }
        }
    }
}

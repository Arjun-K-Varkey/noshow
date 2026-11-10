package io.noshow.service;

import io.noshow.domain.Check;
import io.noshow.domain.CheckEvent;
import io.noshow.domain.CheckStatus;
import io.noshow.domain.NotificationKind;
import io.noshow.domain.PingEvent;
import io.noshow.domain.PingKind;
import io.noshow.repo.CheckEventRepository;
import io.noshow.repo.CheckRepository;
import io.noshow.repo.PingEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * One transactional attempt to record a ping. Kept separate from
 * {@link PingService} so each retry runs in a fresh transaction: the method
 * re-reads the check, so a retry after an optimistic-lock conflict works from
 * the latest committed state. May throw an optimistic-lock failure at commit
 * if a concurrent evaluator sweep updated the check first; the caller retries.
 */
@Component
public class PingWriter {

    public enum Result { NOT_FOUND, RECORDED }

    private final CheckRepository checks;
    private final PingEventRepository pingEvents;
    private final CheckEventRepository checkEvents;
    private final NotificationService notifications;
    private final Clock clock;

    public PingWriter(CheckRepository checks, PingEventRepository pingEvents,
                      CheckEventRepository checkEvents, NotificationService notifications, Clock clock) {
        this.checks = checks;
        this.pingEvents = pingEvents;
        this.checkEvents = checkEvents;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public Result write(String token, PingKind kind, String sourceIp, String userAgent) {
        Check check = checks.findByPingToken(token).orElse(null);
        if (check == null) {
            return Result.NOT_FOUND;
        }

        Instant now = clock.instant();
        pingEvents.save(new PingEvent(UUID.randomUUID(), check.getId(), kind, now, sourceIp, userAgent));

        // Only a SUCCESS ping advances the schedule and flips status to UP.
        // START/FAIL are logged; acting on them comes later.
        if (kind == PingKind.SUCCESS) {
            CheckStatus from = check.getStatus();
            check.recordSuccess(now);
            // Log the recovery (or first-up) transition, but not a routine
            // on-time ping where the status was already UP.
            if (from != CheckStatus.UP) {
                checkEvents.save(new CheckEvent(UUID.randomUUID(), check.getId(),
                        from, CheckStatus.UP, now, "ping"));
            }
            // Alert only on recovery from a DOWN we would have alerted on — not
            // a first-up (NEW) or a soft LATE that never sent a DOWN alert.
            if (from == CheckStatus.DOWN) {
                notifications.enqueue(check, NotificationKind.RECOVERY, now);
            }
        } else {
            check.recordPing(now);
        }
        checks.save(check);
        return Result.RECORDED;
    }
}

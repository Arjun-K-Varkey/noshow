package io.noshow.service;

import io.noshow.domain.Check;
import io.noshow.domain.PingEvent;
import io.noshow.domain.PingKind;
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
    private final Clock clock;

    public PingWriter(CheckRepository checks, PingEventRepository pingEvents, Clock clock) {
        this.checks = checks;
        this.pingEvents = pingEvents;
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

        // M1/M2.3: only a SUCCESS ping advances the schedule and flips status to UP.
        // START/FAIL are logged; acting on them comes later.
        if (kind == PingKind.SUCCESS) {
            check.recordSuccess(now);
        } else {
            check.recordPing(now);
        }
        checks.save(check);
        return Result.RECORDED;
    }
}

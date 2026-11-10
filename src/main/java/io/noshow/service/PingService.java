package io.noshow.service;

import io.noshow.domain.Check;
import io.noshow.domain.PingEvent;
import io.noshow.domain.PingKind;
import io.noshow.repo.CheckRepository;
import io.noshow.repo.PingEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PingService {

    private final CheckRepository checks;
    private final PingEventRepository pingEvents;
    private final Clock clock;

    public PingService(CheckRepository checks, PingEventRepository pingEvents, Clock clock) {
        this.checks = checks;
        this.pingEvents = pingEvents;
        this.clock = clock;
    }

    /**
     * Record a ping for the check with the given token.
     *
     * @return true if the token matched a check, false otherwise.
     */
    @Transactional
    public boolean recordPing(String token, PingKind kind, String sourceIp, String userAgent) {
        Check check = checks.findByPingToken(token).orElse(null);
        if (check == null) {
            return false;
        }

        Instant now = clock.instant();
        pingEvents.save(new PingEvent(UUID.randomUUID(), check.getId(), kind, now, sourceIp, userAgent));

        // M1: only a SUCCESS ping advances the schedule and flips status to UP.
        // START/FAIL are logged; acting on them comes with the evaluator (M2+).
        if (kind == PingKind.SUCCESS) {
            check.recordSuccess(now);
        } else {
            check.recordPing(now);
        }
        checks.save(check);
        return true;
    }
}

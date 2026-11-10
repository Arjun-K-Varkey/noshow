package io.noshow.service;

import io.noshow.domain.Check;
import io.noshow.domain.CheckEvent;
import io.noshow.repo.CheckEventRepository;
import io.noshow.repo.CheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CheckRepository checks;
    private final CheckEventRepository events;
    private final Clock clock;

    public CheckService(CheckRepository checks, CheckEventRepository events, Clock clock) {
        this.checks = checks;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Check create(String name, long periodSeconds, long graceSeconds) {
        Check check = new Check(UUID.randomUUID(), name, periodSeconds, graceSeconds,
                newToken(), clock.instant());
        return checks.save(check);
    }

    @Transactional(readOnly = true)
    public List<Check> listNewestFirst() {
        return checks.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Check> find(UUID id) {
        return checks.findById(id);
    }

    /** When this check last changed status, or null if it never has. */
    @Transactional(readOnly = true)
    public Instant lastTransitionAt(UUID checkId) {
        return events.findFirstByCheckIdOrderByAtDesc(checkId)
                .map(CheckEvent::getAt)
                .orElse(null);
    }

    /** Status-change history for a check, newest first. */
    @Transactional(readOnly = true)
    public List<CheckEvent> history(UUID checkId) {
        return events.findByCheckIdOrderByAtDesc(checkId);
    }

    /** A url-safe random token, unique per check, embedded in the ping URL. */
    private static String newToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

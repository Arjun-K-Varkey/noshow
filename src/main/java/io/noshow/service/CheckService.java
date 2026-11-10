package io.noshow.service;

import io.noshow.domain.Check;
import io.noshow.repo.CheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class CheckService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CheckRepository checks;
    private final Clock clock;

    public CheckService(CheckRepository checks, Clock clock) {
        this.checks = checks;
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

    /** A url-safe random token, unique per check, embedded in the ping URL. */
    private static String newToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

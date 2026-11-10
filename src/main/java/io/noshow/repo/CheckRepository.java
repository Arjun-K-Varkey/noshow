package io.noshow.repo;

import io.noshow.domain.Check;
import io.noshow.domain.CheckStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckRepository extends JpaRepository<Check, UUID> {

    Optional<Check> findByPingToken(String pingToken);

    List<Check> findAllByOrderByCreatedAtDesc();

    List<Check> findByStatusIn(Collection<CheckStatus> statuses);

    /** Checks that are due for evaluation: active and already past expected time. */
    List<Check> findByStatusInAndExpectedAtLessThanEqual(Collection<CheckStatus> statuses, Instant now);
}

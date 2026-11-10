package io.noshow.repo;

import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import io.noshow.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    long countByCheckId(UUID checkId);

    long countByCheckIdAndKind(UUID checkId, NotificationKind kind);

    /** PENDING notifications due for a (first or retry) attempt. */
    List<NotificationOutbox> findByStatusAndNextAttemptAtLessThanEqual(OutboxStatus status, Instant now);
}

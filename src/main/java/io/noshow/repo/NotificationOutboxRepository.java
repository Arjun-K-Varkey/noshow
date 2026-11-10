package io.noshow.repo;

import io.noshow.domain.NotificationKind;
import io.noshow.domain.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    long countByCheckId(UUID checkId);

    long countByCheckIdAndKind(UUID checkId, NotificationKind kind);
}

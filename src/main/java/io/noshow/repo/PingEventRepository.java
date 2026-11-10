package io.noshow.repo;

import io.noshow.domain.PingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PingEventRepository extends JpaRepository<PingEvent, UUID> {

    long countByCheckId(UUID checkId);
}

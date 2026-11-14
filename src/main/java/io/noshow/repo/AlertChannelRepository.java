package io.noshow.repo;

import io.noshow.domain.AlertChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertChannelRepository extends JpaRepository<AlertChannel, UUID> {

    /** Channels eligible for delivery (used by fan-out in M4.3). */
    List<AlertChannel> findByVerifiedTrue();
}

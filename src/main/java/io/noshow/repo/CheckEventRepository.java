package io.noshow.repo;

import io.noshow.domain.CheckEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckEventRepository extends JpaRepository<CheckEvent, UUID> {

    List<CheckEvent> findByCheckIdOrderByAtDesc(UUID checkId);

    Optional<CheckEvent> findFirstByCheckIdOrderByAtDesc(UUID checkId);

    /** Latest transition time per check, in one query — each row is [checkId, max(at)]. */
    @Query("select e.checkId, max(e.at) from CheckEvent e where e.checkId in :ids group by e.checkId")
    List<Object[]> findLatestTransitionAtByCheckIds(@Param("ids") Collection<UUID> ids);
}

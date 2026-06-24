package com.company.bustracking.repository;

import com.company.bustracking.domain.BoardingEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardingEventRepository extends JpaRepository<BoardingEvent, UUID> {
    List<BoardingEvent> findTop1000ByBus_IdAndScannedAtBetweenOrderByScannedAtDesc(
            UUID busId, Instant from, Instant to);
}

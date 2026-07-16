package com.company.bustracking.repository;

import com.company.bustracking.domain.DeviceAssignmentHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceAssignmentHistoryRepository extends JpaRepository<DeviceAssignmentHistory, UUID> {
    Optional<DeviceAssignmentHistory> findByDevice_IdAndRemovedAtIsNull(UUID deviceId);
    List<DeviceAssignmentHistory> findByDevice_IdOrderByInstalledAtDesc(UUID deviceId);
}

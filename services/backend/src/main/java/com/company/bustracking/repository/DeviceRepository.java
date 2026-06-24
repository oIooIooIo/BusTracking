package com.company.bustracking.repository;

import com.company.bustracking.domain.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByHardwareSerialAndActiveTrue(String hardwareSerial);
    Optional<Device> findFirstByBus_IdOrderByActiveDesc(UUID busId);
}

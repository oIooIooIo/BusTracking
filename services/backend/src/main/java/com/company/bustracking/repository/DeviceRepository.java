package com.company.bustracking.repository;

import com.company.bustracking.domain.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByHardwareSerialAndActiveTrue(String hardwareSerial);
    Optional<Device> findFirstByBus_IdOrderByActiveDesc(UUID busId);
    boolean existsByDeviceCodeIgnoreCase(String deviceCode);
    boolean existsByDeviceCodeIgnoreCaseAndIdNot(String deviceCode, UUID id);
    boolean existsByHardwareSerialIgnoreCase(String hardwareSerial);
    boolean existsByHardwareSerialIgnoreCaseAndIdNot(String hardwareSerial, UUID id);
    boolean existsByBus_IdAndActiveTrue(UUID busId);
    boolean existsByBus_IdAndActiveTrueAndIdNot(UUID busId, UUID id);
}

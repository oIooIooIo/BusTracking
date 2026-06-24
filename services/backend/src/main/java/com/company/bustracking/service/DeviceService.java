package com.company.bustracking.service;

import com.company.bustracking.api.DeviceApi.BoardingEventInput;
import com.company.bustracking.api.DeviceApi.GpsPointInput;
import com.company.bustracking.api.DeviceApi.PermissionEmployee;
import com.company.bustracking.api.DeviceApi.PermissionSnapshot;
import com.company.bustracking.api.DeviceApi.RejectedEvent;
import com.company.bustracking.api.DeviceApi.RejectedGpsPoint;
import com.company.bustracking.api.DeviceApi.UploadEventResult;
import com.company.bustracking.api.DeviceApi.UploadGpsResult;
import com.company.bustracking.domain.BoardingEvent;
import com.company.bustracking.domain.BusEmployeePermission;
import com.company.bustracking.domain.Device;
import com.company.bustracking.domain.Employee;
import com.company.bustracking.repository.BoardingEventRepository;
import com.company.bustracking.repository.DeviceRepository;
import com.company.bustracking.repository.EmployeeRepository;
import com.company.bustracking.repository.PermissionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private final PermissionRepository permissions;
    private final EmployeeRepository employees;
    private final BoardingEventRepository events;
    private final DeviceRepository devices;
    private final TrackingStore tracking;

    public DeviceService(
            PermissionRepository permissions,
            EmployeeRepository employees,
            BoardingEventRepository events,
            DeviceRepository devices,
            TrackingStore tracking) {
        this.permissions = permissions;
        this.employees = employees;
        this.events = events;
        this.devices = devices;
        this.tracking = tracking;
    }

    @Transactional
    public PermissionSnapshot permissionSnapshot(Device device) {
        touch(device);
        List<PermissionEmployee> result = permissions
                .findByBus_IdAndEmployee_ActiveTrueOrderByEmployee_EmployeeNo(
                        device.getBus().getId())
                .stream()
                .map(BusEmployeePermission::getEmployee)
                .map(employee -> new PermissionEmployee(
                        employee.getId(),
                        employee.getEmployeeNo(),
                        employee.getName(),
                        employee.getCardSn()))
                .toList();
        return new PermissionSnapshot(
                device.getBus().getPermissionVersion(),
                Instant.now(),
                new com.company.bustracking.api.DeviceApi.PermissionBus(
                        device.getBus().getId(),
                        device.getBus().getCode(),
                        device.getBus().getName()),
                result);
    }

    @Transactional
    public UploadGpsResult uploadGps(Device device, List<GpsPointInput> points) {
        touch(device);
        List<Long> accepted = new ArrayList<>();
        List<RejectedGpsPoint> rejected = new ArrayList<>();
        for (GpsPointInput point : points) {
            if (!validLocation(point)) {
                rejected.add(new RejectedGpsPoint(
                        point.sequenceNo(), "INVALID_LOCATION", false));
                continue;
            }
            TrackingStore.InsertResult result =
                    tracking.insert(device.getId(), device.getBus().getId(), point);
            if (result == TrackingStore.InsertResult.CONFLICT) {
                rejected.add(new RejectedGpsPoint(
                        point.sequenceNo(), "SEQUENCE_CONFLICT", false));
            } else {
                accepted.add(point.sequenceNo());
            }
        }
        return new UploadGpsResult(accepted, rejected);
    }

    @Transactional
    public UploadEventResult uploadEvents(Device device, List<BoardingEventInput> inputs) {
        touch(device);
        List<UUID> accepted = new ArrayList<>();
        List<RejectedEvent> rejected = new ArrayList<>();
        for (BoardingEventInput input : inputs) {
            BoardingEvent existing = events.findById(input.id()).orElse(null);
            if (existing != null) {
                if (same(existing, device, input)) {
                    accepted.add(input.id());
                } else {
                    rejected.add(new RejectedEvent(input.id(), "EVENT_ID_CONFLICT", false));
                }
                continue;
            }

            Employee employee = null;
            if (input.employeeId() != null) {
                employee = employees.findById(input.employeeId()).orElse(null);
                if (employee == null) {
                    rejected.add(new RejectedEvent(input.id(), "UNKNOWN_EMPLOYEE", false));
                    continue;
                }
            }
            events.save(new BoardingEvent(
                    input.id(),
                    device.getBus(),
                    device,
                    employee,
                    input.cardSn().trim(),
                    input.result(),
                    input.scannedAt(),
                    input.permissionVersion()));
            accepted.add(input.id());
        }
        return new UploadEventResult(accepted, rejected);
    }

    private void touch(Device device) {
        device.markSeen();
        devices.save(device);
    }

    private boolean validLocation(GpsPointInput point) {
        return point.sequenceNo() > 0
                && Double.isFinite(point.latitude())
                && point.latitude() >= -90
                && point.latitude() <= 90
                && Double.isFinite(point.longitude())
                && point.longitude() >= -180
                && point.longitude() <= 180
                && (point.accuracyMeters() == null
                    || (Float.isFinite(point.accuracyMeters()) && point.accuracyMeters() >= 0));
    }

    private boolean same(BoardingEvent event, Device device, BoardingEventInput input) {
        return event.getDeviceId().equals(device.getId())
                && event.getBusId().equals(device.getBus().getId())
                && Objects.equals(event.getEmployeeId(), input.employeeId())
                && event.getCardSn().equals(input.cardSn().trim())
                && event.getResult() == input.result()
                && event.getScannedAt().equals(input.scannedAt())
                && Objects.equals(event.getPermissionVersion(), input.permissionVersion());
    }
}

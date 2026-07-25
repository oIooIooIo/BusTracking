package com.company.bustracking.service;

import com.company.bustracking.api.DeviceApi.BoardingEventInput;
import com.company.bustracking.api.DeviceApi.GpsPointInput;
import com.company.bustracking.api.DeviceApi.PermissionEmployee;
import com.company.bustracking.api.DeviceApi.PermissionRoute;
import com.company.bustracking.api.DeviceApi.PermissionStop;
import com.company.bustracking.api.DeviceApi.PermissionSnapshot;
import com.company.bustracking.api.DeviceApi.RejectedEvent;
import com.company.bustracking.api.DeviceApi.RejectedGpsPoint;
import com.company.bustracking.api.DeviceApi.UploadEventResult;
import com.company.bustracking.api.DeviceApi.UploadGpsResult;
import com.company.bustracking.domain.BoardingEvent;
import com.company.bustracking.domain.Device;
import com.company.bustracking.domain.Employee;
import com.company.bustracking.repository.BoardingEventRepository;
import com.company.bustracking.repository.DeviceRepository;
import com.company.bustracking.repository.EmployeeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private final EmployeeRepository employees;
    private final BoardingEventRepository events;
    private final DeviceRepository devices;
    private final TrackingStore tracking;
    private final RouteService routes;

    public DeviceService(
            EmployeeRepository employees,
            BoardingEventRepository events,
            DeviceRepository devices,
            TrackingStore tracking,
            RouteService routes) {
        this.employees = employees;
        this.events = events;
        this.devices = devices;
        this.tracking = tracking;
        this.routes = routes;
    }

    @Transactional
    public PermissionSnapshot permissionSnapshot(Device device) {
        touch(device);
        RouteService.DeviceConfiguration config = routes.configuration(device);
        return new PermissionSnapshot(
                config.version(), Instant.now(),
                new com.company.bustracking.api.DeviceApi.PermissionBus(
                        device.getBus().getId(), device.getBus().getCode(), device.getBus().getName()),
                config.routes().stream().map(route -> new PermissionRoute(
                        route.id(), route.code(), route.name())).toList(),
                config.employees().stream().map(employee -> new PermissionEmployee(
                        employee.id(), employee.employeeNo(), employee.name(), employee.department(),
                        employee.cardSn(), employee.routeIds())).toList(),
                config.stops().stream().map(stop -> new PermissionStop(
                        stop.id(), stop.code(), stop.name(), stop.latitude(), stop.longitude(),
                        stop.radiusMeters(), stop.order())).toList());
    }

    @Transactional
    public void acknowledgeConfiguration(Device device, long version) {
        touch(device);
        routes.acknowledge(device, version);
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
            if (!validEventLocation(input)) {
                rejected.add(new RejectedEvent(input.id(), "INVALID_LOCATION", false));
                continue;
            }
            List<UUID> matchedRoutes = input.routeIds() == null || input.routeIds().isEmpty()
                    ? routes.matchingRoutes(device.getBus().getId(), input.employeeId())
                    : input.routeIds();
            UUID stopId = routes.nearestStop(matchedRoutes, input.latitude(), input.longitude(),
                    input.accuracyMeters(), input.scannedAt(), input.locationRecordedAt());
            String eventType = input.eventType() == null ? "BOARDING" : input.eventType();
            String locationSource = input.locationSource() == null ? "UNAVAILABLE" : input.locationSource();
            BoardingEvent saved = events.saveAndFlush(new BoardingEvent(
                    input.id(), device.getBus(), device, employee, input.cardSn().trim(),
                    input.result(), input.scannedAt(), input.permissionVersion(), eventType,
                    input.employeeNo() != null ? input.employeeNo() : employee == null ? null : employee.getEmployeeNo(),
                    input.employeeName() != null ? input.employeeName() : employee == null ? null : employee.getName(),
                    input.employeeDepartment() != null ? input.employeeDepartment() : employee == null ? null : employee.getDepartment(),
                    input.latitude(), input.longitude(), input.locationRecordedAt(), locationSource,
                    input.accuracyMeters(), stopId));
            routes.linkEventRoutes(saved.getId(), matchedRoutes);
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

    private boolean validEventLocation(BoardingEventInput input) {
        if (input.latitude() == null && input.longitude() == null) return true;
        return input.latitude() != null && input.longitude() != null
                && Double.isFinite(input.latitude()) && input.latitude() >= -90 && input.latitude() <= 90
                && Double.isFinite(input.longitude()) && input.longitude() >= -180 && input.longitude() <= 180
                && (input.accuracyMeters() == null || Float.isFinite(input.accuracyMeters()) && input.accuracyMeters() >= 0);
    }

    private boolean same(BoardingEvent event, Device device, BoardingEventInput input) {
        return event.getDeviceId().equals(device.getId())
                && event.getBusId().equals(device.getBus().getId())
                && Objects.equals(event.getEmployeeId(), input.employeeId())
                && event.getCardSn().equals(input.cardSn().trim())
                && event.getResult() == input.result()
                && event.getScannedAt().equals(input.scannedAt())
                && Objects.equals(event.getPermissionVersion(), input.permissionVersion())
                && Objects.equals(event.getLatitude(), input.latitude())
                && Objects.equals(event.getLongitude(), input.longitude())
                && Objects.equals(event.getLocationRecordedAt(), input.locationRecordedAt())
                && Objects.equals(event.getAccuracyMeters(), input.accuracyMeters());
    }
}

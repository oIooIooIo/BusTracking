package com.company.bustracking.service;

import com.company.bustracking.api.AdminApi.BoardingEventView;
import com.company.bustracking.api.AdminApi.BusInput;
import com.company.bustracking.api.AdminApi.BusView;
import com.company.bustracking.api.AdminApi.EmployeeInput;
import com.company.bustracking.api.AdminApi.EmployeeView;
import com.company.bustracking.api.AdminApi.DeviceInput;
import com.company.bustracking.api.AdminApi.DeviceView;
import com.company.bustracking.domain.BoardingEvent;
import com.company.bustracking.domain.Bus;
import com.company.bustracking.domain.BusEmployeePermission;
import com.company.bustracking.domain.BusEmployeePermissionId;
import com.company.bustracking.domain.Employee;
import com.company.bustracking.domain.Device;
import com.company.bustracking.repository.BoardingEventRepository;
import com.company.bustracking.repository.BusRepository;
import com.company.bustracking.repository.EmployeeRepository;
import com.company.bustracking.repository.DeviceRepository;
import com.company.bustracking.repository.PermissionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final Duration MAX_ROUTE_RANGE = Duration.ofHours(24);

    private final BusRepository buses;
    private final EmployeeRepository employees;
    private final DeviceRepository devices;
    private final PermissionRepository permissions;
    private final BoardingEventRepository events;
    private final TrackingStore tracking;

    public AdminService(
            BusRepository buses,
            EmployeeRepository employees,
            DeviceRepository devices,
            PermissionRepository permissions,
            BoardingEventRepository events,
            TrackingStore tracking) {
        this.buses = buses;
        this.employees = employees;
        this.devices = devices;
        this.permissions = permissions;
        this.events = events;
        this.tracking = tracking;
    }

    @Transactional(readOnly = true)
    public List<BusView> buses() {
        return buses.findAll().stream().map(this::toBusView).toList();
    }

    @Transactional
    public BusView createBus(BusInput input) {
        Bus bus = buses.save(new Bus(
                input.code().trim(),
                input.name().trim(),
                input.active()));
        devices.save(new Device(
                bus.getCode() + "-DEVICE",
                normalizeHardwareSerial(input.hardwareSerial()),
                bus,
                input.active()));
        return toBusView(bus);
    }

    @Transactional
    public BusView updateBus(UUID id, BusInput input) {
        Bus bus = bus(id);
        bus.update(input.code().trim(), input.name().trim(), input.active());
        Device device = devices.findFirstByBus_IdOrderByActiveDesc(id)
                .orElseGet(() -> new Device(
                        bus.getCode() + "-DEVICE",
                        normalizeHardwareSerial(input.hardwareSerial()),
                        bus,
                        input.active()));
        device.update(
                bus.getCode() + "-DEVICE",
                normalizeHardwareSerial(input.hardwareSerial()),
                bus,
                input.active());
        devices.save(device);
        return toBusView(buses.save(bus));
    }

    @Transactional(readOnly = true)
    public List<DeviceView> devices() {
        return devices.findAll().stream().map(AdminService::toView).toList();
    }

    @Transactional
    public DeviceView createDevice(DeviceInput input) {
        return toView(devices.save(new Device(
                input.deviceCode().trim(),
                normalizeHardwareSerial(input.hardwareSerial()),
                bus(input.busId()),
                input.active())));
    }

    @Transactional
    public DeviceView updateDevice(UUID id, DeviceInput input) {
        Device device = devices.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + id));
        device.update(
                input.deviceCode().trim(),
                normalizeHardwareSerial(input.hardwareSerial()),
                bus(input.busId()),
                input.active());
        return toView(devices.save(device));
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> employees() {
        return employees.findAll().stream().map(AdminService::toView).toList();
    }

    @Transactional
    public EmployeeView createEmployee(EmployeeInput input) {
        return toView(employees.save(new Employee(
                input.employeeNo().trim(),
                input.name().trim(),
                input.cardSn().trim(),
                input.active())));
    }

    @Transactional
    public EmployeeView updateEmployee(UUID id, EmployeeInput input) {
        Employee employee = employee(id);
        boolean snapshotChanged = !employee.getEmployeeNo().equals(input.employeeNo().trim())
                || !employee.getName().equals(input.name().trim())
                || !employee.getCardSn().equals(input.cardSn().trim())
                || employee.isActive() != input.active();
        employee.update(
                input.employeeNo().trim(),
                input.name().trim(),
                input.cardSn().trim(),
                input.active());
        employees.save(employee);
        if (snapshotChanged) {
            permissions.findByEmployee_Id(id).stream()
                    .map(BusEmployeePermission::getBus)
                    .forEach(Bus::incrementPermissionVersion);
        }
        return toView(employee);
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> permissions(UUID busId) {
        bus(busId);
        return permissions.findByBus_IdOrderByEmployee_EmployeeNo(busId).stream()
                .map(BusEmployeePermission::getEmployee)
                .map(AdminService::toView)
                .toList();
    }

    @Transactional
    public void grant(UUID busId, UUID employeeId) {
        Bus bus = bus(busId);
        Employee employee = employee(employeeId);
        BusEmployeePermissionId id = new BusEmployeePermissionId(busId, employeeId);
        if (!permissions.existsById(id)) {
            permissions.save(new BusEmployeePermission(bus, employee));
            bus.incrementPermissionVersion();
        }
    }

    @Transactional
    public void revoke(UUID busId, UUID employeeId) {
        Bus bus = bus(busId);
        BusEmployeePermissionId id = new BusEmployeePermissionId(busId, employeeId);
        if (permissions.existsById(id)) {
            permissions.deleteById(id);
            bus.incrementPermissionVersion();
        }
    }

    @Transactional(readOnly = true)
    public List<TrackingStore.RoutePoint> route(UUID busId, Instant from, Instant to) {
        bus(busId);
        validateRange(from, to);
        return tracking.route(busId, from, to);
    }

    @Transactional(readOnly = true)
    public List<BoardingEventView> boardingEvents(UUID busId, Instant from, Instant to) {
        bus(busId);
        validateRange(from, to);
        return events.findTop1000ByBus_IdAndScannedAtBetweenOrderByScannedAtDesc(busId, from, to)
                .stream()
                .map(AdminService::toView)
                .toList();
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (Duration.between(from, to).compareTo(MAX_ROUTE_RANGE) > 0) {
            throw new IllegalArgumentException("The maximum query range is 24 hours");
        }
    }

    private Bus bus(UUID id) {
        return buses.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bus not found: " + id));
    }

    private Employee employee(UUID id) {
        return employees.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + id));
    }

    private BusView toBusView(Bus bus) {
        String hardwareSerial = devices.findFirstByBus_IdOrderByActiveDesc(bus.getId())
                .map(Device::getHardwareSerial)
                .orElse(null);
        return new BusView(
                bus.getId(),
                bus.getCode(),
                bus.getName(),
                hardwareSerial,
                bus.isActive(),
                bus.getPermissionVersion());
    }

    private static DeviceView toView(Device device) {
        return new DeviceView(
                device.getId(),
                device.getDeviceCode(),
                device.getHardwareSerial(),
                device.getBus().getId(),
                device.getBus().getCode(),
                device.isActive(),
                device.getLastSeenAt());
    }

    private static String normalizeHardwareSerial(String hardwareSerial) {
        return hardwareSerial.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static EmployeeView toView(Employee employee) {
        return new EmployeeView(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getCardSn(),
                employee.isActive());
    }

    private static BoardingEventView toView(BoardingEvent event) {
        return new BoardingEventView(
                event.getId(),
                event.getEmployeeId(),
                event.getCardSn(),
                event.getResult(),
                event.getScannedAt(),
                event.getPermissionVersion());
    }
}

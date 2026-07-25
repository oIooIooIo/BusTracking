package com.company.bustracking.service;

import com.company.bustracking.api.AdminApi.BoardingEventView;
import com.company.bustracking.api.AdminApi.BusInput;
import com.company.bustracking.api.AdminApi.BusUpdateInput;
import com.company.bustracking.api.AdminApi.BusView;
import com.company.bustracking.api.AdminApi.DeviceAssignmentView;
import com.company.bustracking.api.AdminApi.DeviceCreateInput;
import com.company.bustracking.api.AdminApi.DeviceUpdateInput;
import com.company.bustracking.api.AdminApi.DeviceView;
import com.company.bustracking.api.AdminApi.EmployeeInput;
import com.company.bustracking.api.AdminApi.EmployeeView;
import com.company.bustracking.domain.BoardingEvent;
import com.company.bustracking.domain.Bus;
import com.company.bustracking.domain.BusEmployeePermission;
import com.company.bustracking.domain.BusEmployeePermissionId;
import com.company.bustracking.domain.Device;
import com.company.bustracking.domain.DeviceAssignmentHistory;
import com.company.bustracking.domain.Employee;
import com.company.bustracking.repository.BoardingEventRepository;
import com.company.bustracking.repository.BusRepository;
import com.company.bustracking.repository.DeviceAssignmentHistoryRepository;
import com.company.bustracking.repository.DeviceRepository;
import com.company.bustracking.repository.EmployeeRepository;
import com.company.bustracking.repository.PermissionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final Duration MAX_ROUTE_RANGE = Duration.ofHours(24);

    private final BusRepository buses;
    private final EmployeeRepository employees;
    private final DeviceRepository devices;
    private final DeviceAssignmentHistoryRepository assignmentHistory;
    private final PermissionRepository permissions;
    private final BoardingEventRepository events;
    private final TrackingStore tracking;
    private final RouteService routes;
    private final JdbcTemplate jdbc;

    public AdminService(BusRepository buses, EmployeeRepository employees, DeviceRepository devices,
            DeviceAssignmentHistoryRepository assignmentHistory, PermissionRepository permissions,
            BoardingEventRepository events, TrackingStore tracking, RouteService routes, JdbcTemplate jdbc) {
        this.buses = buses;
        this.employees = employees;
        this.devices = devices;
        this.assignmentHistory = assignmentHistory;
        this.permissions = permissions;
        this.events = events;
        this.tracking = tracking;
        this.routes = routes;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<BusView> buses() { return buses.findAll().stream().map(this::toBusView).toList(); }

    @Transactional
    public BusView createBus(BusInput input) {
        validateBusIdentity(null, input.code());
        Bus bus = buses.save(new Bus(input.code().trim(), input.name().trim(), input.active()));
        Device device = devices.save(new Device(nextDeviceCode(),
                normalizeHardwareSerial(input.hardwareSerial()), bus, input.active()));
        assignmentHistory.save(new DeviceAssignmentHistory(device, bus, Instant.now()));
        return toBusView(bus);
    }

    @Transactional
    public BusView updateBus(UUID id, BusUpdateInput input) {
        Bus bus = bus(id);
        validateBusIdentity(id, input.code());
        if (bus.isActive() && !input.active() && devices.existsByBus_IdAndActiveTrue(id)) {
            throw new IllegalArgumentException("Deactivate the bus device before retiring this bus");
        }
        if (!bus.isActive() && input.active() && input.clearPermissions() && permissions.countByBus_Id(id) > 0) {
            permissions.deleteByBus_Id(id);
            bus.incrementPermissionVersion();
        }
        bus.update(input.code().trim(), input.name().trim(), input.active());
        return toBusView(buses.save(bus));
    }

    @Transactional(readOnly = true)
    public List<DeviceView> devices() { return devices.findAll().stream().map(AdminService::toView).toList(); }

    @Transactional
    public DeviceView createDevice(DeviceCreateInput input) {
        validateDeviceHardwareSerial(null, input.hardwareSerial());
        Bus bus = activeBus(input.busId());
        validateActiveDevice(null, bus, input.active());
        Device device = devices.save(new Device(nextDeviceCode(),
                normalizeHardwareSerial(input.hardwareSerial()), bus, input.active()));
        assignmentHistory.save(new DeviceAssignmentHistory(device, bus, Instant.now()));
        return toView(device);
    }

    @Transactional
    public DeviceView updateDevice(UUID id, DeviceUpdateInput input) {
        Device device = device(id);
        validateDeviceHardwareSerial(id, input.hardwareSerial());
        Bus targetBus = activeBus(input.busId());
        validateActiveDevice(id, targetBus, input.active());
        boolean reassigned = !device.getBus().getId().equals(targetBus.getId());
        if (reassigned) {
            Instant now = Instant.now();
            assignmentHistory.findByDevice_IdAndRemovedAtIsNull(id).ifPresent(history -> history.close(now));
            assignmentHistory.flush();
            assignmentHistory.save(new DeviceAssignmentHistory(device, targetBus, now));
        }
        device.update(normalizeHardwareSerial(input.hardwareSerial()), targetBus, input.active());
        return toView(devices.save(device));
    }

    @Transactional(readOnly = true)
    public List<DeviceAssignmentView> assignmentHistory(UUID deviceId) {
        device(deviceId);
        return assignmentHistory.findByDevice_IdOrderByInstalledAtDesc(deviceId).stream()
                .map(AdminService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> employees(String employeeNo) {
        var result = employeeNo == null || employeeNo.isBlank()
                ? employees.findAll()
                : employees.findTop50ByEmployeeNoContainingIgnoreCaseOrderByEmployeeNo(employeeNo.trim());
        return result.stream().map(AdminService::toView).toList();
    }

    @Transactional
    public EmployeeView createEmployee(EmployeeInput input) {
        validateEmployeeIdentity(null, input.employeeNo(), input.cardSn());
        return toView(employees.save(new Employee(input.employeeNo().trim(), input.name().trim(), input.department().trim(),
                input.cardSn().trim(), input.active())));
    }

    @Transactional
    public EmployeeView updateEmployee(UUID id, EmployeeInput input) {
        Employee employee = employee(id);
        validateEmployeeIdentity(id, input.employeeNo(), input.cardSn());
        boolean snapshotChanged = !employee.getEmployeeNo().equals(input.employeeNo().trim())
                || !employee.getName().equals(input.name().trim())
                || !employee.getDepartment().equals(input.department().trim())
                || !employee.getCardSn().equals(input.cardSn().trim())
                || employee.isActive() != input.active();
        employee.update(input.employeeNo().trim(), input.name().trim(), input.department().trim(), input.cardSn().trim(), input.active());
        employees.save(employee);
        if (snapshotChanged) {
            permissions.findByEmployee_Id(id).stream().map(BusEmployeePermission::getBus)
                    .forEach(Bus::incrementPermissionVersion);
            routes.bumpForEmployee(id);
        }
        return toView(employee);
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> permissions(UUID busId) {
        bus(busId);
        return permissions.findByBus_IdOrderByEmployee_EmployeeNo(busId).stream()
                .map(BusEmployeePermission::getEmployee).map(AdminService::toView).toList();
    }

    @Transactional
    public void grant(UUID busId, UUID employeeId) { grantBatch(busId, List.of(employeeId)); }

    @Transactional
    public void grantBatch(UUID busId, List<UUID> employeeIds) {
        Bus bus = bus(busId);
        List<UUID> uniqueIds = new LinkedHashSet<>(employeeIds).stream().toList();
        List<Employee> selected = uniqueIds.stream().map(this::employee).toList();
        if (selected.stream().anyMatch(employee -> !employee.isActive())) {
            throw new IllegalArgumentException("Inactive employees cannot be granted boarding permission");
        }
        boolean changed = false;
        for (Employee employee : selected) {
            BusEmployeePermissionId id = new BusEmployeePermissionId(busId, employee.getId());
            if (!permissions.existsById(id)) {
                permissions.save(new BusEmployeePermission(bus, employee));
                changed = true;
            }
        }
        if (changed) bus.incrementPermissionVersion();
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
        bus(busId); validateRange(from, to); return tracking.route(busId, from, to);
    }

    public double dailyMileageKm(List<TrackingStore.RoutePoint> points) {
        return tracking.mileageMeters(points) / 1000.0;
    }

    @Transactional(readOnly = true)
    public List<BoardingEventView> boardingEvents(UUID busId, Instant from, Instant to) {
        bus(busId); validateRange(from, to);
        return events.findTop1000ByBus_IdAndScannedAtBetweenOrderByScannedAtDesc(busId, from, to)
                .stream().map(this::toEventView).toList();
    }

    private void validateBusIdentity(UUID id, String code) {
        String value = code.trim();
        boolean exists = id == null ? buses.existsByCodeIgnoreCase(value) : buses.existsByCodeIgnoreCaseAndIdNot(value, id);
        if (exists) throw new IllegalArgumentException("Bus code already exists");
    }

    private String nextDeviceCode() {
        long sequence = jdbc.queryForObject("SELECT nextval('device_code_seq')", Long.class);
        return "DEVICE-%06d".formatted(sequence);
    }

    private void validateDeviceHardwareSerial(UUID id, String hardwareSerial) {
        String serial = normalizeHardwareSerial(hardwareSerial);
        boolean serialExists = id == null ? devices.existsByHardwareSerialIgnoreCase(serial)
                : devices.existsByHardwareSerialIgnoreCaseAndIdNot(serial, id);
        if (serialExists) throw new IllegalArgumentException("Hardware serial already exists");
    }

    private void validateEmployeeIdentity(UUID id, String employeeNo, String cardSn) {
        String number = employeeNo.trim();
        String serial = cardSn.trim();
        boolean numberExists = id == null ? employees.existsByEmployeeNoIgnoreCase(number) : employees.existsByEmployeeNoIgnoreCaseAndIdNot(number, id);
        boolean cardExists = id == null ? employees.existsByCardSnIgnoreCase(serial) : employees.existsByCardSnIgnoreCaseAndIdNot(serial, id);
        if (numberExists) throw new IllegalArgumentException("Employee number already exists");
        if (cardExists) throw new IllegalArgumentException("CardSN already exists");
    }

    private void validateActiveDevice(UUID deviceId, Bus bus, boolean active) {
        if (!active) return;
        boolean occupied = deviceId == null ? devices.existsByBus_IdAndActiveTrue(bus.getId())
                : devices.existsByBus_IdAndActiveTrueAndIdNot(bus.getId(), deviceId);
        if (occupied) throw new IllegalArgumentException("This bus already has an active device");
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
        if (Duration.between(from, to).compareTo(MAX_ROUTE_RANGE) > 0)
            throw new IllegalArgumentException("The maximum query range is 24 hours");
    }

    private Bus bus(UUID id) { return buses.findById(id).orElseThrow(() -> new EntityNotFoundException("Bus not found: " + id)); }
    private Bus activeBus(UUID id) {
        Bus bus = bus(id);
        if (!bus.isActive()) throw new IllegalArgumentException("Devices can only be assigned to an active bus");
        return bus;
    }
    private Device device(UUID id) { return devices.findById(id).orElseThrow(() -> new EntityNotFoundException("Device not found: " + id)); }
    private Employee employee(UUID id) { return employees.findById(id).orElseThrow(() -> new EntityNotFoundException("Employee not found: " + id)); }

    private BusView toBusView(Bus bus) {
        String serial = devices.findFirstByBus_IdOrderByActiveDesc(bus.getId()).map(Device::getHardwareSerial).orElse(null);
        RouteService.SyncView sync = routes.sync(bus.getId());
        return new BusView(bus.getId(), bus.getCode(), bus.getName(), serial, bus.isActive(),
                bus.getPermissionVersion(), permissions.countByBus_Id(bus.getId()),
                routes.routeSummaries(bus.getId()), sync.desiredVersion(), sync.appliedVersion(),
                sync.synced(), sync.appliedAt());
    }
    private static DeviceView toView(Device device) {
        return new DeviceView(device.getId(), device.getDeviceCode(), device.getHardwareSerial(),
                device.getBus().getId(), device.getBus().getCode(), device.isActive(), device.getLastSeenAt());
    }
    private static DeviceAssignmentView toView(DeviceAssignmentHistory history) {
        return new DeviceAssignmentView(history.getId(), history.getDevice().getId(), history.getBus().getId(),
                history.getBus().getCode(), history.getBus().getName(), history.getInstalledAt(), history.getRemovedAt());
    }
    private static EmployeeView toView(Employee employee) {
        return new EmployeeView(employee.getId(), employee.getEmployeeNo(), employee.getName(),
                employee.getDepartment(), employee.getCardSn(), employee.isActive());
    }
    private BoardingEventView toEventView(BoardingEvent event) {
        return new BoardingEventView(event.getId(), event.getEmployeeId(),
                event.getEmployeeNoSnapshot(), event.getEmployeeNameSnapshot(),
                event.getEmployeeDepartmentSnapshot(), event.getCardSn(), event.getResult(),
                event.getEventType(), event.getScannedAt(), event.getPermissionVersion(),
                event.getBusId(), event.getDeviceId(), routes.eventRoutes(event.getId()),
                event.getStopId(), routes.stopName(event.getStopId()), event.getLatitude(),
                event.getLongitude(), event.getLocationRecordedAt(), event.getLocationSource(),
                event.getAccuracyMeters());
    }
    private static String normalizeHardwareSerial(String value) {
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}

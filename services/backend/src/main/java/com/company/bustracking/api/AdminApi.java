package com.company.bustracking.api;

import com.company.bustracking.domain.BoardingResult;
import com.company.bustracking.service.AdminService;
import com.company.bustracking.service.TrackingStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1")
public class AdminApi {
    private final AdminService service;
    private final com.company.bustracking.service.RouteService routes;
    public AdminApi(AdminService service, com.company.bustracking.service.RouteService routes) {
        this.service = service;
        this.routes = routes;
    }

    @GetMapping("/buses") List<BusView> buses() { return service.buses(); }
    @PostMapping("/buses") @ResponseStatus(HttpStatus.CREATED)
    BusView createBus(@Valid @RequestBody BusInput input) { return service.createBus(input); }
    @PutMapping("/buses/{busId}")
    BusView updateBus(@PathVariable UUID busId, @Valid @RequestBody BusUpdateInput input) {
        return service.updateBus(busId, input);
    }

    @GetMapping("/stops")
    List<com.company.bustracking.service.RouteService.StopView> stops(
            @RequestParam(required = false) String q, @RequestParam(required = false) Boolean active) {
        return routes.catalogStops(q, active);
    }
    @PostMapping("/stops") @ResponseStatus(HttpStatus.CREATED)
    com.company.bustracking.service.RouteService.StopView createStop(@Valid @RequestBody StopInput input) {
        return routes.createStop(input.toService());
    }
    @PutMapping("/stops/{stopId}")
    com.company.bustracking.service.RouteService.StopView updateStop(@PathVariable UUID stopId,
            @Valid @RequestBody StopInput input) { return routes.updateStop(stopId, input.toService()); }
    @GetMapping("/stops/{stopId}/routes")
    List<com.company.bustracking.service.RouteService.RouteSummary> stopRoutes(@PathVariable UUID stopId) {
        return routes.stopRoutes(stopId);
    }

    @GetMapping("/routes")
    List<com.company.bustracking.service.RouteService.RouteView> routes() { return routes.routes(); }
    @PostMapping("/routes") @ResponseStatus(HttpStatus.CREATED)
    com.company.bustracking.service.RouteService.RouteView createRoute(@Valid @RequestBody RouteInput input) {
        return routes.create(input.toService());
    }
    @PutMapping("/routes/{routeId}")
    com.company.bustracking.service.RouteService.RouteView updateRoute(@PathVariable UUID routeId,
            @Valid @RequestBody RouteInput input) { return routes.update(routeId, input.toService()); }
    @GetMapping("/routes/{routeId}/permissions")
    List<com.company.bustracking.service.RouteService.EmployeeView> routePermissions(@PathVariable UUID routeId) {
        return routes.permissions(routeId);
    }
    @PostMapping("/routes/{routeId}/permissions/batch") @ResponseStatus(HttpStatus.NO_CONTENT)
    void grantRoutePermissions(@PathVariable UUID routeId, @Valid @RequestBody PermissionBatchInput input) {
        routes.grant(routeId, input.employeeIds());
    }
    @DeleteMapping("/routes/{routeId}/permissions/{employeeId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeRoutePermission(@PathVariable UUID routeId, @PathVariable UUID employeeId) {
        routes.revoke(routeId, employeeId);
    }
    @GetMapping("/buses/{busId}/routes")
    List<UUID> assignedRoutes(@PathVariable UUID busId) { return routes.assignedRoutes(busId); }
    @PutMapping("/buses/{busId}/routes") @ResponseStatus(HttpStatus.NO_CONTENT)
    void assignRoutes(@PathVariable UUID busId, @Valid @RequestBody RouteAssignmentInput input) {
        routes.assignRoutes(busId, input.routeIds());
    }

    @GetMapping("/devices") List<DeviceView> devices() { return service.devices(); }
    @PostMapping("/devices") @ResponseStatus(HttpStatus.CREATED)
    DeviceView createDevice(@Valid @RequestBody DeviceInput input) { return service.createDevice(input); }
    @PutMapping("/devices/{deviceId}")
    DeviceView updateDevice(@PathVariable UUID deviceId, @Valid @RequestBody DeviceInput input) {
        return service.updateDevice(deviceId, input);
    }
    @GetMapping("/devices/{deviceId}/assignment-history")
    List<DeviceAssignmentView> assignmentHistory(@PathVariable UUID deviceId) {
        return service.assignmentHistory(deviceId);
    }

    @GetMapping("/employees")
    List<EmployeeView> employees(@RequestParam(required = false) String employeeNo) {
        return service.employees(employeeNo);
    }
    @PostMapping("/employees") @ResponseStatus(HttpStatus.CREATED)
    EmployeeView createEmployee(@Valid @RequestBody EmployeeInput input) { return service.createEmployee(input); }
    @PutMapping("/employees/{employeeId}")
    EmployeeView updateEmployee(@PathVariable UUID employeeId, @Valid @RequestBody EmployeeInput input) {
        return service.updateEmployee(employeeId, input);
    }

    @GetMapping("/buses/{busId}/permissions")
    List<EmployeeView> permissions(@PathVariable UUID busId) { return service.permissions(busId); }
    @PutMapping("/buses/{busId}/permissions/{employeeId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void grant(@PathVariable UUID busId, @PathVariable UUID employeeId) { service.grant(busId, employeeId); }
    @PostMapping("/buses/{busId}/permissions/batch") @ResponseStatus(HttpStatus.NO_CONTENT)
    void grantBatch(@PathVariable UUID busId, @Valid @RequestBody PermissionBatchInput input) {
        service.grantBatch(busId, input.employeeIds());
    }
    @DeleteMapping("/buses/{busId}/permissions/{employeeId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID busId, @PathVariable UUID employeeId) { service.revoke(busId, employeeId); }

    @GetMapping("/buses/{busId}/gps-points")
    RouteHistory route(@PathVariable UUID busId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<TrackingStore.RoutePoint> points = service.route(busId, from, to);
        return new RouteHistory(busId, from, to, service.dailyMileageKm(points), points);
    }
    @GetMapping("/buses/{busId}/boarding-events")
    List<BoardingEventView> boardingEvents(@PathVariable UUID busId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.boardingEvents(busId, from, to);
    }

    public record BusInput(@NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String hardwareSerial, boolean active) {}
    public record BusUpdateInput(@NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name, boolean active, boolean clearPermissions) {}
    public record BusView(UUID id, String code, String name, String hardwareSerial,
            boolean active, long permissionVersion, long permissionCount,
            List<com.company.bustracking.service.RouteService.RouteSummary> routes,
            long desiredConfigurationVersion, Long appliedConfigurationVersion,
            boolean configurationSynced, Instant configurationAppliedAt) {}
    public record DeviceInput(@NotBlank @Size(max = 100) String deviceCode,
            @NotBlank @Size(max = 100) String hardwareSerial, @NotNull UUID busId, boolean active) {}
    public record DeviceView(UUID id, String deviceCode, String hardwareSerial, UUID busId,
            String busCode, boolean active, Instant lastSeenAt) {}
    public record DeviceAssignmentView(UUID id, UUID deviceId, UUID busId, String busCode,
            String busName, Instant installedAt, Instant removedAt) {}
    public record EmployeeInput(@NotBlank @Size(max = 50) String employeeNo,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String department,
            @NotBlank @Size(max = 100) String cardSn, boolean active) {}
    public record EmployeeView(UUID id, String employeeNo, String name, String department, String cardSn, boolean active) {}
    public record StopInput(@NotBlank @Size(max = 100) String name,
            double latitude, double longitude, float radiusMeters, boolean active) {
        com.company.bustracking.service.RouteService.StopInput toService() {
            return new com.company.bustracking.service.RouteService.StopInput(
                    name, latitude, longitude, radiusMeters, active);
        }
    }
    public record RouteInput(@NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name, boolean active,
            @NotNull List<@NotNull UUID> stopIds) {
        com.company.bustracking.service.RouteService.RouteInput toService() {
            return new com.company.bustracking.service.RouteService.RouteInput(code, name, active, stopIds);
        }
    }
    public record RouteAssignmentInput(@NotNull List<@NotNull UUID> routeIds) {}
    public record PermissionBatchInput(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> employeeIds) {}
    public record RouteHistory(UUID busId, Instant from, Instant to, double dailyMileageKm,
            List<TrackingStore.RoutePoint> points) {}
    public record BoardingEventView(UUID id, UUID employeeId, String employeeNo, String employeeName,
            String employeeDepartment, String cardSn, BoardingResult result, String eventType,
            Instant scannedAt, Long permissionVersion, UUID busId, UUID deviceId,
            List<com.company.bustracking.service.RouteService.RouteSummary> routes, UUID stopId,
            String stopName, Double latitude, Double longitude, Instant locationRecordedAt,
            String locationSource, Float accuracyMeters) {}
}

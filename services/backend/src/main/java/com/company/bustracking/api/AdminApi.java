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

    public AdminApi(AdminService service) {
        this.service = service;
    }

    @GetMapping("/buses")
    List<BusView> buses() {
        return service.buses();
    }

    @PostMapping("/buses")
    @ResponseStatus(HttpStatus.CREATED)
    BusView createBus(@Valid @RequestBody BusInput input) {
        return service.createBus(input);
    }

    @PutMapping("/buses/{busId}")
    BusView updateBus(@PathVariable UUID busId, @Valid @RequestBody BusInput input) {
        return service.updateBus(busId, input);
    }

    @GetMapping("/devices")
    List<DeviceView> devices() {
        return service.devices();
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    DeviceView createDevice(@Valid @RequestBody DeviceInput input) {
        return service.createDevice(input);
    }

    @PutMapping("/devices/{deviceId}")
    DeviceView updateDevice(
            @PathVariable UUID deviceId,
            @Valid @RequestBody DeviceInput input) {
        return service.updateDevice(deviceId, input);
    }

    @GetMapping("/employees")
    List<EmployeeView> employees() {
        return service.employees();
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    EmployeeView createEmployee(@Valid @RequestBody EmployeeInput input) {
        return service.createEmployee(input);
    }

    @PutMapping("/employees/{employeeId}")
    EmployeeView updateEmployee(
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeInput input) {
        return service.updateEmployee(employeeId, input);
    }

    @GetMapping("/buses/{busId}/permissions")
    List<EmployeeView> permissions(@PathVariable UUID busId) {
        return service.permissions(busId);
    }

    @PutMapping("/buses/{busId}/permissions/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void grant(@PathVariable UUID busId, @PathVariable UUID employeeId) {
        service.grant(busId, employeeId);
    }

    @DeleteMapping("/buses/{busId}/permissions/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID busId, @PathVariable UUID employeeId) {
        service.revoke(busId, employeeId);
    }

    @GetMapping("/buses/{busId}/gps-points")
    RouteHistory route(
            @PathVariable UUID busId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return new RouteHistory(busId, from, to, service.route(busId, from, to));
    }

    @GetMapping("/buses/{busId}/boarding-events")
    List<BoardingEventView> boardingEvents(
            @PathVariable UUID busId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.boardingEvents(busId, from, to);
    }

    public record BusInput(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String hardwareSerial,
            boolean active) {}

    public record BusView(
            UUID id,
            String code,
            String name,
            String hardwareSerial,
            boolean active,
            long permissionVersion) {}

    public record DeviceInput(
            @NotBlank @Size(max = 100) String deviceCode,
            @NotBlank @Size(max = 100) String hardwareSerial,
            @NotNull UUID busId,
            boolean active) {}

    public record DeviceView(
            UUID id,
            String deviceCode,
            String hardwareSerial,
            UUID busId,
            String busCode,
            boolean active,
            Instant lastSeenAt) {}

    public record EmployeeInput(
            @NotBlank @Size(max = 50) String employeeNo,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String cardSn,
            boolean active) {}

    public record EmployeeView(
            UUID id,
            String employeeNo,
            String name,
            String cardSn,
            boolean active) {}

    public record RouteHistory(
            UUID busId,
            Instant from,
            Instant to,
            List<TrackingStore.RoutePoint> points) {}

    public record BoardingEventView(
            UUID id,
            UUID employeeId,
            String cardSn,
            BoardingResult result,
            Instant scannedAt,
            Long permissionVersion) {}
}

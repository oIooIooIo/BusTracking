package com.company.bustracking.api;

import com.company.bustracking.domain.BoardingResult;
import com.company.bustracking.domain.Device;
import com.company.bustracking.security.DeviceAuthenticationFilter;
import com.company.bustracking.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device/v1")
public class DeviceApi {
    private final DeviceService service;

    public DeviceApi(DeviceService service) {
        this.service = service;
    }

    @GetMapping("/permissions")
    PermissionSnapshot permissions(HttpServletRequest request) {
        return service.permissionSnapshot(device(request));
    }

    @PostMapping("/configuration-ack")
    void acknowledgeConfiguration(HttpServletRequest request,
            @Valid @RequestBody ConfigurationAck body) {
        service.acknowledgeConfiguration(device(request), body.version());
    }

    @PostMapping("/gps-points/batch")
    UploadGpsResult uploadGps(
            HttpServletRequest request,
            @Valid @RequestBody GpsBatch body) {
        return service.uploadGps(device(request), body.points());
    }

    @PostMapping("/boarding-events/batch")
    UploadEventResult uploadEvents(
            HttpServletRequest request,
            @Valid @RequestBody EventBatch body) {
        return service.uploadEvents(device(request), body.events());
    }

    private Device device(HttpServletRequest request) {
        return (Device) request.getAttribute(DeviceAuthenticationFilter.DEVICE_ATTRIBUTE);
    }

    public record PermissionSnapshot(
            long version,
            Instant generatedAt,
            PermissionBus bus,
            List<PermissionRoute> routes,
            List<PermissionEmployee> employees,
            List<PermissionStop> stops) {}

    public record PermissionBus(UUID id, String code, String name) {}

    public record PermissionRoute(UUID id, String code, String name) {}
    public record PermissionEmployee(UUID id, String employeeNo, String name, String department,
            String cardSn, List<UUID> routeIds) {}
    public record PermissionStop(UUID id, String code, String name, double latitude,
            double longitude, float radiusMeters, int order) {}
    public record ConfigurationAck(@Positive long version) {}

    public record GpsBatch(
            @NotEmpty @Size(max = 500) List<@Valid GpsPointInput> points) {}

    public record GpsPointInput(
            @Positive long sequenceNo,
            @NotNull Instant recordedAt,
            double latitude,
            double longitude,
            Float accuracyMeters) {}

    public record UploadGpsResult(
            List<Long> acceptedSequenceNos,
            List<RejectedGpsPoint> rejected) {}

    public record RejectedGpsPoint(long sequenceNo, String code, boolean retryable) {}

    public record EventBatch(
            @NotEmpty @Size(max = 200) List<@Valid BoardingEventInput> events) {}

    public record BoardingEventInput(
            @NotNull UUID id,
            @NotBlank @Size(max = 100) String cardSn,
            UUID employeeId,
            @NotNull BoardingResult result,
            @NotNull Instant scannedAt,
            Long permissionVersion,
            @Size(max = 20) String eventType,
            @Size(max = 50) String employeeNo,
            @Size(max = 100) String employeeName,
            @Size(max = 100) String employeeDepartment,
            Double latitude,
            Double longitude,
            Instant locationRecordedAt,
            @Size(max = 20) String locationSource,
            Float accuracyMeters,
            List<UUID> routeIds) {}

    public record UploadEventResult(
            List<UUID> acceptedIds,
            List<RejectedEvent> rejected) {}

    public record RejectedEvent(UUID id, String code, boolean retryable) {}
}

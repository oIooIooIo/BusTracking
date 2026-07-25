package com.company.bustracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "boarding_event")
public class BoardingEvent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id")
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "card_sn", nullable = false)
    private String cardSn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardingResult result;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "permission_version")
    private Long permissionVersion;

    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "employee_no_snapshot")
    private String employeeNoSnapshot;
    @Column(name = "employee_name_snapshot")
    private String employeeNameSnapshot;
    @Column(name = "employee_department_snapshot")
    private String employeeDepartmentSnapshot;
    private Double latitude;
    private Double longitude;
    @Column(name = "location_recorded_at")
    private Instant locationRecordedAt;
    @Column(name = "location_source", nullable = false)
    private String locationSource;
    @Column(name = "accuracy_meters")
    private Float accuracyMeters;
    @Column(name = "stop_id")
    private UUID stopId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected BoardingEvent() {}

    public BoardingEvent(
            UUID id,
            Bus bus,
            Device device,
            Employee employee,
            String cardSn,
            BoardingResult result,
            Instant scannedAt,
            Long permissionVersion,
            String eventType,
            String employeeNoSnapshot,
            String employeeNameSnapshot,
            String employeeDepartmentSnapshot,
            Double latitude,
            Double longitude,
            Instant locationRecordedAt,
            String locationSource,
            Float accuracyMeters,
            UUID stopId) {
        this.id = id;
        this.bus = bus;
        this.device = device;
        this.employee = employee;
        this.cardSn = cardSn;
        this.result = result;
        this.scannedAt = scannedAt;
        this.permissionVersion = permissionVersion;
        this.eventType = eventType;
        this.employeeNoSnapshot = employeeNoSnapshot;
        this.employeeNameSnapshot = employeeNameSnapshot;
        this.employeeDepartmentSnapshot = employeeDepartmentSnapshot;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationRecordedAt = locationRecordedAt;
        this.locationSource = locationSource;
        this.accuracyMeters = accuracyMeters;
        this.stopId = stopId;
        this.receivedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBusId() { return bus.getId(); }
    public UUID getDeviceId() { return device.getId(); }
    public UUID getEmployeeId() { return employee == null ? null : employee.getId(); }
    public String getCardSn() { return cardSn; }
    public BoardingResult getResult() { return result; }
    public Instant getScannedAt() { return scannedAt; }
    public Long getPermissionVersion() { return permissionVersion; }
    public String getEventType() { return eventType; }
    public String getEmployeeNoSnapshot() { return employeeNoSnapshot; }
    public String getEmployeeNameSnapshot() { return employeeNameSnapshot; }
    public String getEmployeeDepartmentSnapshot() { return employeeDepartmentSnapshot; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Instant getLocationRecordedAt() { return locationRecordedAt; }
    public String getLocationSource() { return locationSource; }
    public Float getAccuracyMeters() { return accuracyMeters; }
    public UUID getStopId() { return stopId; }
}

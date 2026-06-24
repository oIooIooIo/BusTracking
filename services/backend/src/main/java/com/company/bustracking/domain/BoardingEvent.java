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
            Long permissionVersion) {
        this.id = id;
        this.bus = bus;
        this.device = device;
        this.employee = employee;
        this.cardSn = cardSn;
        this.result = result;
        this.scannedAt = scannedAt;
        this.permissionVersion = permissionVersion;
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
}

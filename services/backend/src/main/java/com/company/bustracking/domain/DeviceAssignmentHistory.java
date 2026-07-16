package com.company.bustracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_assignment_history")
public class DeviceAssignmentHistory {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "device_id") private Device device;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bus_id") private Bus bus;
    @Column(name = "installed_at", nullable = false) private Instant installedAt;
    @Column(name = "removed_at") private Instant removedAt;

    protected DeviceAssignmentHistory() {}
    public DeviceAssignmentHistory(Device device, Bus bus, Instant installedAt) {
        this.id = UUID.randomUUID(); this.device = device; this.bus = bus; this.installedAt = installedAt;
    }
    public void close(Instant removedAt) { this.removedAt = removedAt; }
    public UUID getId() { return id; }
    public Device getDevice() { return device; }
    public Bus getBus() { return bus; }
    public Instant getInstalledAt() { return installedAt; }
    public Instant getRemovedAt() { return removedAt; }
}

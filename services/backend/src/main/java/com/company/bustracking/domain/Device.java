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
@Table(name = "device")
public class Device {
    @Id
    private UUID id;

    @Column(name = "device_code", nullable = false, unique = true)
    private String deviceCode;

    @Column(name = "hardware_serial", nullable = false, unique = true)
    private String hardwareSerial;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bus_id")
    private Bus bus;

    @Column(name = "api_key_hash")
    private String apiKeyHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected Device() {}

    public Device(String deviceCode, String hardwareSerial, Bus bus, boolean active) {
        this.id = UUID.randomUUID();
        this.deviceCode = deviceCode;
        this.hardwareSerial = hardwareSerial;
        this.bus = bus;
        this.active = active;
    }

    public UUID getId() { return id; }
    public String getDeviceCode() { return deviceCode; }
    public String getHardwareSerial() { return hardwareSerial; }
    public Bus getBus() { return bus; }
    public String getApiKeyHash() { return apiKeyHash; }
    public boolean isActive() { return active; }
    public Instant getLastSeenAt() { return lastSeenAt; }

    public void update(String deviceCode, String hardwareSerial, Bus bus, boolean active) {
        this.deviceCode = deviceCode;
        this.hardwareSerial = hardwareSerial;
        this.bus = bus;
        this.active = active;
    }

    public void markSeen() {
        this.lastSeenAt = Instant.now();
    }
}

package com.company.bustracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bus")
public class Bus {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "permission_version", nullable = false)
    private long permissionVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bus() {}

    public Bus(String code, String name, boolean active) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.active = active;
        this.permissionVersion = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String code, String name, boolean active) {
        this.code = code;
        this.name = name;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void incrementPermissionVersion() {
        this.permissionVersion++;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public long getPermissionVersion() { return permissionVersion; }
}

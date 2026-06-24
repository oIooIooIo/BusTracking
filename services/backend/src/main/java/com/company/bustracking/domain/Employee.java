package com.company.bustracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee")
public class Employee {
    @Id
    private UUID id;

    @Column(name = "employee_no", nullable = false, unique = true, length = 50)
    private String employeeNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "card_sn", nullable = false, unique = true, length = 100)
    private String cardSn;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {}

    public Employee(String employeeNo, String name, String cardSn, boolean active) {
        this.id = UUID.randomUUID();
        this.employeeNo = employeeNo;
        this.name = name;
        this.cardSn = cardSn;
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String employeeNo, String name, String cardSn, boolean active) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.cardSn = cardSn;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmployeeNo() { return employeeNo; }
    public String getName() { return name; }
    public String getCardSn() { return cardSn; }
    public boolean isActive() { return active; }
}

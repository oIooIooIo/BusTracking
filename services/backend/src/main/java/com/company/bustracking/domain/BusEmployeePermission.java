package com.company.bustracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bus_employee_permission")
@IdClass(BusEmployeePermissionId.class)
public class BusEmployeePermission {
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id")
    private Bus bus;

    @Id
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BusEmployeePermission() {}

    public BusEmployeePermission(Bus bus, Employee employee) {
        this.bus = bus;
        this.employee = employee;
        this.createdAt = Instant.now();
    }

    public Bus getBus() { return bus; }
    public Employee getEmployee() { return employee; }
}

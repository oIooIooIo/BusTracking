package com.company.bustracking.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BusEmployeePermissionId implements Serializable {
    private UUID bus;
    private UUID employee;

    public BusEmployeePermissionId() {}

    public BusEmployeePermissionId(UUID bus, UUID employee) {
        this.bus = bus;
        this.employee = employee;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof BusEmployeePermissionId other)) return false;
        return Objects.equals(bus, other.bus) && Objects.equals(employee, other.employee);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bus, employee);
    }
}

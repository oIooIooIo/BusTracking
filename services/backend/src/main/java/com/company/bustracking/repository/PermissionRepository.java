package com.company.bustracking.repository;

import com.company.bustracking.domain.BusEmployeePermission;
import com.company.bustracking.domain.BusEmployeePermissionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository
        extends JpaRepository<BusEmployeePermission, BusEmployeePermissionId> {
    List<BusEmployeePermission> findByBus_IdAndEmployee_ActiveTrueOrderByEmployee_EmployeeNo(UUID busId);
    List<BusEmployeePermission> findByBus_IdOrderByEmployee_EmployeeNo(UUID busId);
    List<BusEmployeePermission> findByEmployee_Id(UUID employeeId);
}

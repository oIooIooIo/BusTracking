package com.company.bustracking.repository;

import com.company.bustracking.domain.Employee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findTop50ByEmployeeNoContainingIgnoreCaseOrderByEmployeeNo(String employeeNo);
    boolean existsByEmployeeNoIgnoreCase(String employeeNo);
    boolean existsByEmployeeNoIgnoreCaseAndIdNot(String employeeNo, UUID id);
    boolean existsByCardSnIgnoreCase(String cardSn);
    boolean existsByCardSnIgnoreCaseAndIdNot(String cardSn, UUID id);
}

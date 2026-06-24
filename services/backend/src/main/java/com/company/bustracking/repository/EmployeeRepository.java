package com.company.bustracking.repository;

import com.company.bustracking.domain.Employee;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {}

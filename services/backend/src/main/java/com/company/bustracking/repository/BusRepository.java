package com.company.bustracking.repository;

import com.company.bustracking.domain.Bus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusRepository extends JpaRepository<Bus, UUID> {}

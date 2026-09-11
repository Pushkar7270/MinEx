package com.minex.backend.repo;

import com.minex.backend.domain.Anomaly;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {
    long countByStatus(String status);
}

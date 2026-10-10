package com.alphamath.simulation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SimulationJobRepository extends JpaRepository<SimulationJobEntity, String> {
  Optional<SimulationJobEntity> findFirstByStatusOrderByCreatedAtAsc(String status);

  long countByStatus(String status);

  long countByStatusAndUserId(String status, String userId);

  long countByStatusInAndUserId(List<String> statuses, String userId);

  List<SimulationJobEntity> findByStatusAndStartedAtBefore(String status, Instant cutoff);
}

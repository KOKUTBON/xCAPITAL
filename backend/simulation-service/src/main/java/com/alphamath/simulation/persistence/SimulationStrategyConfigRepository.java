package com.alphamath.simulation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulationStrategyConfigRepository extends JpaRepository<SimulationStrategyConfigEntity, String> {
  Optional<SimulationStrategyConfigEntity> findFirstByHash(String hash);
  Optional<SimulationStrategyConfigEntity> findTopByStrategyOrderByVersionDesc(String strategy);
}

package com.alphamath.simulation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "simulation_strategy_config")
@Data
public class SimulationStrategyConfigEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String strategy;

  @Column(nullable = false)
  private Integer version;

  @Column(nullable = false)
  private String hash;

  @Lob
  @Column(nullable = false)
  private String configJson;

  @Column(nullable = false)
  private Instant createdAt;
}

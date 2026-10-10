package com.alphamath.simulation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "simulation_job")
@Data
public class SimulationJobEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String status;

  @Lob
  @Column(name = "request_json", nullable = false)
  private String requestJson;

  @Lob
  @Column(name = "result_json")
  private String resultJson;

  @Lob
  private String error;

  @Column(name = "user_id")
  private String userId;

  private String workerId;
  private Integer attempts;
  private Instant startedAt;
  private String lastError;

  private String strategyConfigId;
  private Integer strategyConfigVersion;
  private String returnsHash;

  private Instant createdAt;
  private Instant updatedAt;
}

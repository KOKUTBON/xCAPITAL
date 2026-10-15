package com.alphamath.simulation.model;

import lombok.Data;

import java.time.Instant;

@Data
public class SimulationJobResponse {
  private String id;
  private SimulationJobStatus status;
  private SimulationRequest request;
  private SimulationResult result;
  private String error;
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

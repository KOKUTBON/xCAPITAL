package com.alphamath.simulation.model;

import lombok.Data;

import java.time.Instant;

@Data
public class SimulationQuotaStatus {
  private String userId;
  private long pending;
  private long running;
  private long active;
  private int maxPending;
  private int maxRunning;
  private int maxActive;
  private long queueDepth;
  private int maxQueueDepth;
  private Instant asOf;
}

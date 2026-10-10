package com.alphamath.simulation.model;

import lombok.Data;

import java.time.Instant;

@Data
public class SimulationCapacity {
  private String workerId;
  private int maxConcurrency;
  private int inFlight;
  private int executorQueueCapacity;
  private int pollBatchSize;
  private long blockMs;
  private int maxQueueDepth;
  private long queueDepth;
  private long pendingJobs;
  private long runningJobs;
  private Instant asOf;
}

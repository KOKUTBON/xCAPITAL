package com.alphamath.simulation.jobs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulation.worker")
public class SimulationWorkerProperties {
  private int maxConcurrency = 4;
  private int pollBatchSize = 10;
  private long blockMs = 200;
  private int executorQueueCapacity = 100;
  private int maxQueueDepth = 500;
  private int maxAttempts = 3;
  private long staleAfterMinutes = 30;
  private long delayMs = 1000;

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public void setMaxConcurrency(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
  }

  public int getPollBatchSize() {
    return pollBatchSize;
  }

  public void setPollBatchSize(int pollBatchSize) {
    this.pollBatchSize = pollBatchSize;
  }

  public long getBlockMs() {
    return blockMs;
  }

  public void setBlockMs(long blockMs) {
    this.blockMs = blockMs;
  }

  public int getExecutorQueueCapacity() {
    return executorQueueCapacity;
  }

  public void setExecutorQueueCapacity(int executorQueueCapacity) {
    this.executorQueueCapacity = executorQueueCapacity;
  }

  public int getMaxQueueDepth() {
    return maxQueueDepth;
  }

  public void setMaxQueueDepth(int maxQueueDepth) {
    this.maxQueueDepth = maxQueueDepth;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public long getStaleAfterMinutes() {
    return staleAfterMinutes;
  }

  public void setStaleAfterMinutes(long staleAfterMinutes) {
    this.staleAfterMinutes = staleAfterMinutes;
  }

  public long getDelayMs() {
    return delayMs;
  }

  public void setDelayMs(long delayMs) {
    this.delayMs = delayMs;
  }
}

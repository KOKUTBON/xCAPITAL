package com.alphamath.simulation.jobs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulation.quota")
public class SimulationQuotaProperties {
  private int maxPendingPerUser = 20;
  private int maxRunningPerUser = 2;
  private int maxActivePerUser = 25;

  public int getMaxPendingPerUser() {
    return maxPendingPerUser;
  }

  public void setMaxPendingPerUser(int maxPendingPerUser) {
    this.maxPendingPerUser = maxPendingPerUser;
  }

  public int getMaxRunningPerUser() {
    return maxRunningPerUser;
  }

  public void setMaxRunningPerUser(int maxRunningPerUser) {
    this.maxRunningPerUser = maxRunningPerUser;
  }

  public int getMaxActivePerUser() {
    return maxActivePerUser;
  }

  public void setMaxActivePerUser(int maxActivePerUser) {
    this.maxActivePerUser = maxActivePerUser;
  }
}

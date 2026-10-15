package com.alphamath.portfolio.domain.wealth;

import lombok.Data;

@Data
public class WealthPlanSimulationResult {
  private String planId;
  private int simulationCount;
  private double successProbability;
  private double medianOutcome;
  private double p10Outcome;
  private double p90Outcome;
}

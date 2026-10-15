package com.alphamath.portfolio.domain.wealth;

import lombok.Data;

import java.time.Instant;

@Data
public class WealthPlan {
  private String id;
  private String userId;
  private WealthPlanType planType;
  private String name;
  private double startingBalance;
  private double targetBalance;
  private double monthlyContribution;
  private int horizonYears;
  private Double expectedReturn;
  private Double volatility;
  private Integer simulationCount;
  private Double successProbability;
  private Double medianOutcome;
  private Double p10Outcome;
  private Double p90Outcome;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastSimulatedAt;
}

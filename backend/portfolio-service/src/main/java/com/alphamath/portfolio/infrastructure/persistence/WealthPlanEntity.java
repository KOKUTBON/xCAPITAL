package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_wealth_plan")
@Data
public class WealthPlanEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String planType;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private double startingBalance;

  @Column(nullable = false)
  private double targetBalance;

  @Column(nullable = false)
  private double monthlyContribution;

  @Column(nullable = false)
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

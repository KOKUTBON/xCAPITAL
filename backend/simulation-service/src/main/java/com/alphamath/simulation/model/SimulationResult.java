package com.alphamath.simulation.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SimulationResult {
  private String id;
  private Strategy strategy;
  private int periods;
  private double initialCash;
  private double contribution;
  private int contributionEvery;
  private int contributionCount;
  private double totalContributed;
  private double finalEquity;
  private double totalReturn;
  private double maxDrawdown;
  private double meanReturn;
  private double stdDev;
  private List<Double> equityCurve;
  private List<Double> drawdownCurve;
  private Instant createdAt;
  private String disclaimer;
}

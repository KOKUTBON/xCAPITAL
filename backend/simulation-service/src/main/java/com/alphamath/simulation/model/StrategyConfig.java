package com.alphamath.simulation.model;

import lombok.Data;

@Data
public class StrategyConfig {
  private Strategy strategy;
  private double initialCash;
  private double contribution;
  private int contributionEvery;
}

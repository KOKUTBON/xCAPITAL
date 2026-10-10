package com.alphamath.portfolio.domain.wealth;

import lombok.Data;

@Data
public class WealthPlanSimulationRequest {
  private Integer simulationCount;
  private Double expectedReturn;
  private Double volatility;
}

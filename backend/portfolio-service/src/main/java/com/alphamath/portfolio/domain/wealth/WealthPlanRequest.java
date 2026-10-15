package com.alphamath.portfolio.domain.wealth;

import lombok.Data;

@Data
public class WealthPlanRequest {
  private WealthPlanType planType = WealthPlanType.GENERAL_INVESTING;
  private String name;
  private Double startingBalance;
  private Double targetBalance;
  private Double monthlyContribution;
  private Integer horizonYears;
  private Double expectedReturn;
  private Double volatility;
  private Integer simulationCount;
}

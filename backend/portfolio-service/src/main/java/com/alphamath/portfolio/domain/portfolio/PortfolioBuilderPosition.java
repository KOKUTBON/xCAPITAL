package com.alphamath.portfolio.domain.portfolio;

import lombok.Data;

@Data
public class PortfolioBuilderPosition {
  private String symbol;
  private double value;
  private double weight;
  private String sector;
  private String assetClass;
}

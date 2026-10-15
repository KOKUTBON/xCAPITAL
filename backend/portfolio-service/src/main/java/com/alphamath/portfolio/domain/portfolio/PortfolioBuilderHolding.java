package com.alphamath.portfolio.domain.portfolio;

import lombok.Data;

@Data
public class PortfolioBuilderHolding {
  private String symbol;
  private Double quantity;
  private Double price;
}

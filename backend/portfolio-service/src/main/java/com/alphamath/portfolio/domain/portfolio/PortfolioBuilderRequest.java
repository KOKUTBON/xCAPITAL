package com.alphamath.portfolio.domain.portfolio;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PortfolioBuilderRequest {
  private List<PortfolioBuilderHolding> holdings = new ArrayList<>();
  private String baseCurrency = "USD";
}

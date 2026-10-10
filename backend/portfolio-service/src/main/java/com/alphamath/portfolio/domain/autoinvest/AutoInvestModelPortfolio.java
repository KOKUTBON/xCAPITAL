package com.alphamath.portfolio.domain.autoinvest;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AutoInvestModelPortfolio {
  private String id;
  private String name;
  private String riskLevel;
  private String description;
  private Map<String, Double> allocations = new LinkedHashMap<>();
}

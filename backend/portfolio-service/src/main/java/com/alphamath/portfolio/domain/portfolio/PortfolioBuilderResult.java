package com.alphamath.portfolio.domain.portfolio;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class PortfolioBuilderResult {
  private double totalValue;
  private Map<String, Double> sectorWeights = new LinkedHashMap<>();
  private Map<String, Double> assetClassWeights = new LinkedHashMap<>();
  private List<PortfolioBuilderPosition> positions = new ArrayList<>();
  private double concentrationHhi;
  private double diversificationScore;
  private List<String> notes = new ArrayList<>();
}

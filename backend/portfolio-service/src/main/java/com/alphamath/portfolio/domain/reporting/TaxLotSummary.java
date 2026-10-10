package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

@Data
public class TaxLotSummary {
  private String symbol;
  private double openQuantity;
  private double openCostBasis;
  private int openLots;
  private int closedLots;
}

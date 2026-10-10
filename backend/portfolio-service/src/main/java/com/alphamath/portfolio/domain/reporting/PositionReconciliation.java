package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

@Data
public class PositionReconciliation {
  private String symbol;
  private double ledgerQuantity;
  private double positionQuantity;
  private double delta;
}

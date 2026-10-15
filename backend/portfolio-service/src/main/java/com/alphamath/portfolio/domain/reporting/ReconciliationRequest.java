package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

@Data
public class ReconciliationRequest {
  private String accountId;
  private boolean applyPositions;
  private boolean rebuildTaxLots;
  private String lotMethod = "FIFO";
}

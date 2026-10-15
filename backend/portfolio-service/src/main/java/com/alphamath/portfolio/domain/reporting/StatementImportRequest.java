package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

@Data
public class StatementImportRequest {
  private String accountId;
  private String source = "statement";
  private String csv;
  private String delimiter = ",";
  private boolean hasHeader = true;
  private String defaultCurrency = "USD";
  private boolean applyPositions;
  private boolean rebuildTaxLots;
  private String lotMethod = "FIFO";
}

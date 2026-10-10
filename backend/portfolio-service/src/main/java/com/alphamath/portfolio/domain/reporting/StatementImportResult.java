package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class StatementImportResult {
  private String accountId;
  private String source;
  private Instant importedAt;
  private int totalRows;
  private int ingestedRows;
  private int failedRows;
  private List<StatementImportError> errors = new ArrayList<>();
  private ReconciliationReport reconciliation;
}

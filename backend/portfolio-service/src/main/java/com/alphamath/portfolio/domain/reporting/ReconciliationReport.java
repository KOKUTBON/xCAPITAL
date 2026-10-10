package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReconciliationReport {
  private String accountId;
  private Instant asOf;
  private int ledgerEntryCount;
  private int positionCount;
  private int taxLotCount;
  private boolean positionsApplied;
  private boolean taxLotsRebuilt;
  private List<PositionReconciliation> positions = new ArrayList<>();
  private List<TaxLotSummary> taxLots = new ArrayList<>();
}

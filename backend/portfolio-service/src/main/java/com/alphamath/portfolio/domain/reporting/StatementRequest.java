package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class StatementRequest {
  private String accountId;
  private Instant periodStart;
  private Instant periodEnd;
  private String baseCurrency;
  private Double startingBalance;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

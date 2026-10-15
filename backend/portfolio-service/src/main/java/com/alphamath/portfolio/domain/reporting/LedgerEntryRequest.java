package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class LedgerEntryRequest {
  private String accountId;
  private String brokerAccountId;
  private String entryType;
  private String symbol;
  private String instrumentId;
  private String assetClass;
  private Double quantity;
  private Double price;
  private Double amount;
  private String currency;
  private Double fxRate;
  private Instant tradeDate;
  private Instant settleDate;
  private String description;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

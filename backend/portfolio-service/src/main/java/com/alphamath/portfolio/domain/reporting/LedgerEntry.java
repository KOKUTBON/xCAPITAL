package com.alphamath.portfolio.domain.reporting;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class LedgerEntry {
  private String id;
  @JsonIgnore
  private String userId;
  private String accountId;
  private String brokerAccountId;
  private LedgerEntryType entryType;
  private String symbol;
  private String instrumentId;
  private AssetClass assetClass;
  private Double quantity;
  private Double price;
  private Double amount;
  private String currency;
  private Double fxRate;
  private Instant tradeDate;
  private Instant settleDate;
  private String description;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
}

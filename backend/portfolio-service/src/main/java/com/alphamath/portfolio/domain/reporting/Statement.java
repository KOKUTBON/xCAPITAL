package com.alphamath.portfolio.domain.reporting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class Statement {
  private String id;
  @JsonIgnore
  private String userId;
  private String accountId;
  private Instant periodStart;
  private Instant periodEnd;
  private String baseCurrency;
  private Double startingBalance;
  private Double endingBalance;
  private Double deposits;
  private Double withdrawals;
  private Double dividends;
  private Double fees;
  private Double realizedPnl;
  private Double unrealizedPnl;
  private Double netCashFlow;
  private Double tradeNotional;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
}

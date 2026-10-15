package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BrokerPosition {
  private String id;
  @JsonIgnore
  private String userId;
  private String brokerAccountId;
  private String instrumentId;
  private String symbol;
  private AssetClass assetClass;
  private double quantity;
  private Double avgPrice;
  private Double marketPrice;
  private Double marketValue;
  private Double costBasis;
  private Double unrealizedPnl;
  private String currency;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant updatedAt;
}

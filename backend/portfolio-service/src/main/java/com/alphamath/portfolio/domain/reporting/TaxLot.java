package com.alphamath.portfolio.domain.reporting;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TaxLot {
  private String id;
  @JsonIgnore
  private String userId;
  private String accountId;
  private String symbol;
  private String instrumentId;
  private AssetClass assetClass;
  private double quantity;
  private Double costBasis;
  private Double costPerUnit;
  private Instant acquiredAt;
  private Instant disposedAt;
  private TaxLotStatus status = TaxLotStatus.OPEN;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant updatedAt;
}

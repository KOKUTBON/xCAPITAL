package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TaxLotRequest {
  private String id;
  private String accountId;
  private String symbol;
  private String instrumentId;
  private String assetClass;
  private Double quantity;
  private Double costBasis;
  private Double costPerUnit;
  private Instant acquiredAt;
  private Instant disposedAt;
  private String status;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

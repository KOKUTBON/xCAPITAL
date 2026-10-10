package com.alphamath.portfolio.domain.reporting;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class CorporateAction {
  private String id;
  @JsonIgnore
  private String userId;
  private String accountId;
  private CorporateActionType actionType;
  private String symbol;
  private String instrumentId;
  private AssetClass assetClass;
  private Double ratio;
  private Double cashAmount;
  private LocalDate effectiveDate;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
}

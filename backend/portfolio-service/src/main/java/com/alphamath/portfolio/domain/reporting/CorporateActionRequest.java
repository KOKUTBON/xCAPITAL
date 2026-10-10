package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class CorporateActionRequest {
  private String accountId;
  private String actionType;
  private String symbol;
  private String instrumentId;
  private String assetClass;
  private Double ratio;
  private Double cashAmount;
  private LocalDate effectiveDate;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

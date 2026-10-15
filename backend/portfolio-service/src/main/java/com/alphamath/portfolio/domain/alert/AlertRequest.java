package com.alphamath.portfolio.domain.alert;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AlertRequest {
  private String alertType;
  private String symbol;
  private String instrumentId;
  private String assetClass;
  private String comparison;
  private Double targetValue;
  private String frequency;
  private Map<String, Object> condition = new LinkedHashMap<>();
  private Double aiScore;
  private String aiSummary;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

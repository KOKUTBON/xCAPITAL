package com.alphamath.portfolio.domain.alert;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class Alert {
  private String id;
  @JsonIgnore
  private String userId;
  private AlertStatus status;
  private AlertType alertType;
  private String symbol;
  private String instrumentId;
  private AssetClass assetClass;
  private AlertComparison comparison;
  private Double targetValue;
  private AlertFrequency frequency;
  private Map<String, Object> condition = new LinkedHashMap<>();
  private Double aiScore;
  private String aiSummary;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastTriggeredAt;
  private Instant lastCheckedAt;
}

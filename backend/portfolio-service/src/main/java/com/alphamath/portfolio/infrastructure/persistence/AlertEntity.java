package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.alert.AlertComparison;
import com.alphamath.portfolio.domain.alert.AlertFrequency;
import com.alphamath.portfolio.domain.alert.AlertStatus;
import com.alphamath.portfolio.domain.alert.AlertType;
import com.alphamath.portfolio.domain.execution.AssetClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_alert")
@Data
public class AlertEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Enumerated(EnumType.STRING)
  private AlertStatus status;

  @Enumerated(EnumType.STRING)
  private AlertType alertType;

  private String symbol;

  private String instrumentId;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  @Enumerated(EnumType.STRING)
  private AlertComparison comparison;

  private Double targetValue;

  @Enumerated(EnumType.STRING)
  private AlertFrequency frequency;

  @Lob
  private String conditionJson;

  private Double aiScore;

  private String aiSummary;

  @Lob
  private String metadataJson;

  private Instant createdAt;

  private Instant updatedAt;

  private Instant lastTriggeredAt;

  private Instant lastCheckedAt;
}

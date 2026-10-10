package com.alphamath.portfolio.infrastructure.persistence;

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
@Table(name = "portfolio_broker_position")
@Data
public class BrokerPositionEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String brokerAccountId;

  private String instrumentId;

  @Column(nullable = false)
  private String symbol;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  @Column(nullable = false)
  private double quantity;

  private Double avgPrice;

  private Double marketPrice;

  private Double marketValue;

  private Double costBasis;

  private Double unrealizedPnl;

  @Column(nullable = false)
  private String currency;

  @Lob
  private String metadataJson;

  private Instant updatedAt;
}

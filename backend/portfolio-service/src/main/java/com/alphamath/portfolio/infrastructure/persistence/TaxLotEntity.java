package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reporting.TaxLotStatus;
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
@Table(name = "portfolio_tax_lot")
@Data
public class TaxLotEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String accountId;

  @Column(nullable = false)
  private String symbol;

  private String instrumentId;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  @Column(nullable = false)
  private double quantity;

  private Double costBasis;

  private Double costPerUnit;

  private Instant acquiredAt;

  private Instant disposedAt;

  @Enumerated(EnumType.STRING)
  private TaxLotStatus status;

  @Lob
  private String metadataJson;

  private Instant createdAt;

  private Instant updatedAt;
}

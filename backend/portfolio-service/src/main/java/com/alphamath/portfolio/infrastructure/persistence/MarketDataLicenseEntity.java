package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_market_data_license")
@Data
public class MarketDataLicenseEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String provider;

  @Column(nullable = false)
  private String status;

  private String plan;

  @Lob
  @Column(nullable = false)
  private String assetClassesJson;

  @Lob
  @Column(nullable = false)
  private String exchangesJson;

  @Lob
  @Column(nullable = false)
  private String regionsJson;

  private Instant startsAt;
  private Instant endsAt;

  @Lob
  @Column(nullable = false)
  private String metadataJson;

  private Instant createdAt;
  private Instant updatedAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_market_data_entitlement")
@Data
public class MarketDataEntitlementEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String entitlementType;

  private String entitlementValue;

  @Column(nullable = false)
  private String status;

  private String source;

  private Instant createdAt;
  private Instant updatedAt;
}

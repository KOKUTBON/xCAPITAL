package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_funding_deposit")
@Data
public class FundingDepositEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String sourceId;

  @Column(nullable = false)
  private double amount;

  private String currency;

  @Column(nullable = false)
  private String status;

  private String note;
  private String providerId;
  private String providerReference;
  private Instant createdAt;
  private Instant updatedAt;
}

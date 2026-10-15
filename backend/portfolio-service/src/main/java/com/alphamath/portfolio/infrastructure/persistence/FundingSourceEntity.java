package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.funding.FundingMethodType;
import com.alphamath.portfolio.domain.funding.FundingStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_funding_source")
@Data
public class FundingSourceEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Enumerated(EnumType.STRING)
  private FundingMethodType methodType;

  private String providerId;
  private String providerReference;
  private String label;
  private String last4;
  private String currency;
  private String network;

  @Enumerated(EnumType.STRING)
  private FundingStatus status;

  private Instant createdAt;
  private Instant updatedAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_account")
@Data
public class PortfolioAccountEntity {
  @Id
  private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(name = "account_type", nullable = false)
  private String accountType;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private double cash;

  @Column(nullable = false)
  private String currency;

  private Instant createdAt;
  private Instant updatedAt;
}

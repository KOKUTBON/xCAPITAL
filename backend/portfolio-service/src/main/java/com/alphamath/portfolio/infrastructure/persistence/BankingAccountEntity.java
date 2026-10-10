package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_banking_account")
@Data
public class BankingAccountEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private double cash;

  @Column(nullable = false)
  private String currency;

  private Instant createdAt;
  private Instant updatedAt;
}

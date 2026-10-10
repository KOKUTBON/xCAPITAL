package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_banking_transfer")
@Data
public class BankingTransferEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String direction;

  @Column(nullable = false)
  private double amount;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private String status;

  private String note;

  private Instant createdAt;
}

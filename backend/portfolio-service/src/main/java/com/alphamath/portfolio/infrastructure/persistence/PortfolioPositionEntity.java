package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_position")
@Data
public class PortfolioPositionEntity {
  @Id
  private String id;

  @Column(name = "account_id", nullable = false)
  private String accountId;

  @Column(nullable = false)
  private String symbol;

  @Column(nullable = false)
  private double quantity;

  private Instant updatedAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_statement")
@Data
public class StatementEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String accountId;

  @Column(nullable = false)
  private Instant periodStart;

  @Column(nullable = false)
  private Instant periodEnd;

  @Column(nullable = false)
  private String baseCurrency;

  private Double startingBalance;

  private Double endingBalance;

  private Double deposits;

  private Double withdrawals;

  private Double dividends;

  private Double fees;

  private Double realizedPnl;

  private Double unrealizedPnl;

  private Double netCashFlow;

  private Double tradeNotional;

  private String metadataJson;

  private Instant createdAt;
}

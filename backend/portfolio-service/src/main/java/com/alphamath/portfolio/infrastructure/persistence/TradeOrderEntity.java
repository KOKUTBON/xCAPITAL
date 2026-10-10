package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_trade_order")
@Data
public class TradeOrderEntity {
  @Id
  private String id;

  @Column(name = "proposal_id", nullable = false)
  private String proposalId;

  @Column(nullable = false)
  private String symbol;

  @Column(nullable = false)
  private String side;

  @Column(nullable = false)
  private double quantity;

  @Column(nullable = false)
  private double price;

  @Column(nullable = false)
  private double notional;

  @Column(nullable = false)
  private double fee;

  private Instant createdAt;
}

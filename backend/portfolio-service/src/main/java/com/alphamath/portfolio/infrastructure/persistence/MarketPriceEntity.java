package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_market_price")
@Data
public class MarketPriceEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String symbol;

  @Column(name = "ts", nullable = false)
  private Instant ts;

  @Column(nullable = false)
  private double open;

  @Column(nullable = false)
  private double high;

  @Column(nullable = false)
  private double low;

  @Column(nullable = false)
  private double close;

  private Double volume;

  @Column(nullable = false)
  private String source;

  private Instant createdAt;
}

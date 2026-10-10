package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_fx_rate")
@Data
public class FxRateEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String baseCcy;

  @Column(nullable = false)
  private String quoteCcy;

  @Column(nullable = false)
  private double rate;

  @Column(nullable = false)
  private Instant ts;

  @Column(nullable = false)
  private String source;

  private Instant createdAt;
}

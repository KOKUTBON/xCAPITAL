package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_currency")
@Data
public class CurrencyEntity {
  @Id
  private String code;

  @Column(nullable = false)
  private String name;

  private String symbol;

  private int decimals;

  private Instant createdAt;
}

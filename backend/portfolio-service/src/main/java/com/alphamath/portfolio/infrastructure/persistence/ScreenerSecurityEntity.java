package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "portfolio_screener_security")
@Data
public class ScreenerSecurityEntity {
  @Id
  private String symbol;

  @Column(nullable = false)
  private String name;

  private String sector;
  private String industry;
  private Double marketCap;
  private Double peRatio;
  private Double dividendYield;
  private String assetClass;
  private String instrumentType;
  private String currency;
  private String rating;
  private Double priceTarget;

  @Column(nullable = false)
  private boolean focusList;
}

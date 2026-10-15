package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_exchange")
@Data
public class ExchangeEntity {
  @Id
  private String code;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private Region region;

  @Column(nullable = false)
  private String timezone;

  private String mic;

  private String currency;

  private String openTime;

  private String closeTime;

  private Instant createdAt;

  private Instant updatedAt;
}

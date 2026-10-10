package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "portfolio_exchange_calendar")
@Data
public class ExchangeCalendarEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String exchangeCode;

  @Column(nullable = false)
  private LocalDate sessionDate;

  @Column(nullable = false)
  private String status;

  private String openTime;
  private String closeTime;
  private String notes;
  private Instant createdAt;
  private Instant updatedAt;
}

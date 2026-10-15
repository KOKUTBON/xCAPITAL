package com.alphamath.portfolio.domain.reference;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class ExchangeCalendarDay {
  private String exchangeCode;
  private LocalDate sessionDate;
  private ExchangeSessionStatus status;
  private String openTime;
  private String closeTime;
  private String notes;
  private Instant createdAt;
  private Instant updatedAt;
}

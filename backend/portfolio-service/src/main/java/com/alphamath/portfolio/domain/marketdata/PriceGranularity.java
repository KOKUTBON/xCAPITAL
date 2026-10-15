package com.alphamath.portfolio.domain.marketdata;

import java.time.temporal.ChronoUnit;

public enum PriceGranularity {
  MINUTE,
  HOUR,
  DAY;

  public ChronoUnit toChronoUnit() {
    return switch (this) {
      case MINUTE -> ChronoUnit.MINUTES;
      case HOUR -> ChronoUnit.HOURS;
      case DAY -> ChronoUnit.DAYS;
    };
  }
}

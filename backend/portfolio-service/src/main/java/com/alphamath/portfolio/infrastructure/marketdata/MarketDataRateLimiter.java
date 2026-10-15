package com.alphamath.portfolio.infrastructure.marketdata;

import java.time.Duration;
import java.time.Instant;

public class MarketDataRateLimiter {
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final int limit;
  private Instant windowStart;
  private int used;

  public MarketDataRateLimiter(int limitPerMinute) {
    this.limit = limitPerMinute;
  }

  public synchronized boolean tryAcquire() {
    return tryAcquire(1);
  }

  public synchronized boolean tryAcquire(int permits) {
    if (limit <= 0) {
      return true;
    }
    Instant now = Instant.now();
    if (windowStart == null || Duration.between(windowStart, now).compareTo(WINDOW) >= 0) {
      windowStart = now;
      used = 0;
    }
    if (used + permits > limit) {
      return false;
    }
    used += permits;
    return true;
  }
}

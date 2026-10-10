package com.alphamath.portfolio.domain.reference;

import lombok.Data;

import java.time.Instant;

@Data
public class FxRate {
  private String id;
  private String baseCcy;
  private String quoteCcy;
  private double rate;
  private Instant timestamp;
  private String source;
  private Instant createdAt;
}

package com.alphamath.portfolio.domain.surveillance;

import lombok.Data;

import java.time.Instant;

@Data
public class SurveillanceAlert {
  private String id;
  private String userId;
  private String alertType;
  private SurveillanceSeverity severity;
  private String symbol;
  private Double notional;
  private String detail;
  private Instant createdAt;
}

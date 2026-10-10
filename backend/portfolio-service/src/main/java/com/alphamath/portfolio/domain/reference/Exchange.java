package com.alphamath.portfolio.domain.reference;

import com.alphamath.portfolio.domain.execution.Region;
import lombok.Data;

import java.time.Instant;

@Data
public class Exchange {
  private String code;
  private String name;
  private Region region;
  private String timezone;
  private String mic;
  private String currency;
  private String openTime;
  private String closeTime;
  private Instant createdAt;
  private Instant updatedAt;
}

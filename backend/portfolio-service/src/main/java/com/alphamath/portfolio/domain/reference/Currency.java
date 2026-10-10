package com.alphamath.portfolio.domain.reference;

import lombok.Data;

import java.time.Instant;

@Data
public class Currency {
  private String code;
  private String name;
  private String symbol;
  private int decimals = 2;
  private Instant createdAt;
}

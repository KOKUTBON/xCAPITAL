package com.alphamath.portfolio.domain.trade;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class PaperAccount {
  private double cash;
  private Map<String, Double> positions = new LinkedHashMap<>();
  private Instant updatedAt = Instant.now();
}

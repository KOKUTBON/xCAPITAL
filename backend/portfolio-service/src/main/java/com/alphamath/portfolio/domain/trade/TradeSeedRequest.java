package com.alphamath.portfolio.domain.trade;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TradeSeedRequest {
  @NotNull
  @DecimalMin("0.0")
  private Double cash = 0.0;

  private Map<String, Double> positions = new LinkedHashMap<>();
}

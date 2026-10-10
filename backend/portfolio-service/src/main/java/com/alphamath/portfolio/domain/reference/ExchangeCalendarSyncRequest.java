package com.alphamath.portfolio.domain.reference;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ExchangeCalendarSyncRequest {
  @NotBlank
  private String providerId;

  private LocalDate start;
  private LocalDate end;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

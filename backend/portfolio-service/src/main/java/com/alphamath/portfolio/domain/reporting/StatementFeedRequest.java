package com.alphamath.portfolio.domain.reporting;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class StatementFeedRequest {
  @NotBlank
  private String providerId;

  @NotBlank
  private String accountId;

  private Instant start;
  private Instant end;

  private boolean applyPositions = true;
  private boolean rebuildTaxLots = true;
  private String lotMethod = "FIFO";

  private Map<String, Object> metadata = new LinkedHashMap<>();
}

package com.alphamath.portfolio.domain.autoinvest;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AutoInvestRun {
  private String id;
  private String planId;
  private String userId;
  private AutoInvestTrigger trigger;
  private AutoInvestRunStatus status;
  private String idempotencyKey;
  private String proposalId;
  private String reason;
  private Map<String, Object> metrics = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant updatedAt;
}

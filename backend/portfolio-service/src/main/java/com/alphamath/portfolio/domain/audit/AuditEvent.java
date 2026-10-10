package com.alphamath.portfolio.domain.audit;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AuditEvent {
  private String id;
  private String userId;
  private String orgId;
  private String actor;
  private String eventType;
  private String entityType;
  private String entityId;
  private Instant createdAt;
  private String prevHash;
  private String eventHash;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

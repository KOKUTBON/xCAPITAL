package com.alphamath.portfolio.domain.broker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BrokerConnection {
  private String id;
  @JsonIgnore
  private String userId;
  private String brokerId;
  private BrokerConnectionStatus status;
  private String label;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastSyncedAt;
}

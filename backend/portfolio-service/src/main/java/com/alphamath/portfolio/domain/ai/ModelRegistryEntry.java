package com.alphamath.portfolio.domain.ai;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ModelRegistryEntry {
  private String id;
  private String modelName;
  private String version;
  private ModelStatus status;
  private Instant trainingStart;
  private Instant trainingEnd;
  private Map<String, Object> metrics = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant deployedAt;
}

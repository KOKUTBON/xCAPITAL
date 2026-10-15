package com.alphamath.portfolio.domain.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ModelRegisterRequest {
  @NotNull @Size(min = 2, max = 64)
  private String modelName;

  @NotNull @Size(min = 1, max = 64)
  private String version;

  private Instant trainingStart;
  private Instant trainingEnd;
  private Map<String, Object> metrics = new LinkedHashMap<>();
  private ModelStatus status = ModelStatus.DEPLOYED;
}

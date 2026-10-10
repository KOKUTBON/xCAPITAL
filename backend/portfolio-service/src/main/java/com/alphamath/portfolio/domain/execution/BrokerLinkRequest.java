package com.alphamath.portfolio.domain.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BrokerLinkRequest {
  @NotBlank
  private String providerId;

  @NotNull
  private Region region = Region.US;

  private List<AssetClass> assetClasses = new ArrayList<>();

  private String label;

  private Map<String, Object> metadata = new LinkedHashMap<>();
}

package com.alphamath.portfolio.domain.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MarketDataLicense {
  private String id;
  @JsonIgnore
  private String userId;
  private String provider;
  private MarketDataLicenseStatus status;
  private String plan;
  private List<String> assetClasses = new ArrayList<>();
  private List<String> exchanges = new ArrayList<>();
  private List<String> regions = new ArrayList<>();
  private Instant startsAt;
  private Instant endsAt;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant updatedAt;
}

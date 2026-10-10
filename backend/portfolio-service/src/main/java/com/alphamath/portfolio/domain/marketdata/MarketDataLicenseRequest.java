package com.alphamath.portfolio.domain.marketdata;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MarketDataLicenseRequest {
  private String id;
  private String provider;
  private String status;
  private String plan;
  private List<String> assetClasses = new ArrayList<>();
  private List<String> exchanges = new ArrayList<>();
  private List<String> regions = new ArrayList<>();
  private Instant startsAt;
  private Instant endsAt;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

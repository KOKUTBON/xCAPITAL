package com.alphamath.portfolio.domain.execution;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BrokerProviderInfo {
  private String id;
  private String displayName;
  private List<Region> regions = new ArrayList<>();
  private List<AssetClass> assetClasses = new ArrayList<>();
  private List<String> features = new ArrayList<>();
  private int score;
  private String notes;
}

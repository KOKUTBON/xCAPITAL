package com.alphamath.portfolio.domain.marketdata;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.Region;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MarketDataLicenseCatalogEntry {
  private String provider;
  private String plan;
  private List<AssetClass> assetClasses = new ArrayList<>();
  private List<String> exchanges = new ArrayList<>();
  private List<Region> regions = new ArrayList<>();
  private String notes;
}

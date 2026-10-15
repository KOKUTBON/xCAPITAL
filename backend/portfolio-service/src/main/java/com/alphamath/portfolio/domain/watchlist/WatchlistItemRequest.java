package com.alphamath.portfolio.domain.watchlist;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class WatchlistItemRequest {
  private String symbol;
  private String instrumentId;
  private String assetClass;
  private String notes;
  private Double aiScore;
  private String aiSummary;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

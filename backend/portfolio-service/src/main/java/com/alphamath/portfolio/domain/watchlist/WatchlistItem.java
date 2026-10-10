package com.alphamath.portfolio.domain.watchlist;

import com.alphamath.portfolio.domain.execution.AssetClass;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class WatchlistItem {
  private String id;
  private String watchlistId;
  private String symbol;
  private String instrumentId;
  private AssetClass assetClass;
  private String notes;
  private Double aiScore;
  private String aiSummary;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
}

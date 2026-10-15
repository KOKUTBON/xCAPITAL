package com.alphamath.portfolio.domain.marketdata;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MarketDataBackfillResult {
  private String source;
  private int requestedSymbols;
  private int processedSymbols;
  private int ingestedRows;
  private List<String> missingSymbols = new ArrayList<>();
  private Map<String, Integer> ingestedBySymbol = new LinkedHashMap<>();
  private Instant startedAt;
  private Instant finishedAt;
}

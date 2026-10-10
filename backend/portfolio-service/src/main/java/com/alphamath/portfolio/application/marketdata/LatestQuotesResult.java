package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketQuote;

import java.util.List;

public record LatestQuotesResult(List<QuoteSnapshot> quotes, List<String> missing, int cacheHits, int fetched) {
  public record QuoteSnapshot(MarketQuote quote, boolean cacheHit) {}
}

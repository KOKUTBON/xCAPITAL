package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.domain.marketdata.PriceRange;

import java.util.List;
import java.util.Map;

public interface MarketDataProvider {
  Map<String, MarketQuote> getLatestQuotes(List<String> symbols);
  List<MarketPrice> getHistoricalPrices(String symbol, PriceRange range, PriceGranularity granularity, int limit);
}

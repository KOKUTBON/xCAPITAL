package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.domain.marketdata.PriceRange;
import com.alphamath.portfolio.infrastructure.marketdata.CsvMarketDataProvider;
import com.alphamath.portfolio.infrastructure.marketdata.DatabaseMarketDataProvider;
import com.alphamath.portfolio.infrastructure.marketdata.HttpMarketDataProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Primary
public class MarketDataProviderRouter implements MarketDataProvider {
  private final DatabaseMarketDataProvider databaseProvider;
  private final CsvMarketDataProvider csvProvider;
  private final HttpMarketDataProvider httpProvider;

  public MarketDataProviderRouter(DatabaseMarketDataProvider databaseProvider,
                                  CsvMarketDataProvider csvProvider,
                                  HttpMarketDataProvider httpProvider) {
    this.databaseProvider = databaseProvider;
    this.csvProvider = csvProvider;
    this.httpProvider = httpProvider;
  }

  @Override
  public Map<String, MarketQuote> getLatestQuotes(List<String> symbols) {
    Map<String, MarketQuote> out = new LinkedHashMap<>();
    if (symbols == null || symbols.isEmpty()) {
      return out;
    }

    List<String> remaining = new ArrayList<>(symbols);
    if (httpProvider.isEnabled()) {
      Map<String, MarketQuote> httpQuotes = httpProvider.getLatestQuotes(symbols);
      out.putAll(httpQuotes);
      remaining.removeIf(httpQuotes::containsKey);
    }

    if (csvProvider.isEnabled()) {
      Map<String, MarketQuote> csvQuotes = csvProvider.getLatestQuotes(remaining);
      out.putAll(csvQuotes);
      remaining.removeIf(csvQuotes::containsKey);
    }

    if (!remaining.isEmpty()) {
      out.putAll(databaseProvider.getLatestQuotes(remaining));
    }
    return out;
  }

  @Override
  public List<MarketPrice> getHistoricalPrices(String symbol, PriceRange range,
                                               PriceGranularity granularity, int limit) {
    if (httpProvider.isEnabled()) {
      List<MarketPrice> http = httpProvider.getHistoricalPrices(symbol, range, granularity, limit);
      if (!http.isEmpty()) {
        return http;
      }
    }
    if (csvProvider.isEnabled()) {
      List<MarketPrice> csv = csvProvider.getHistoricalPrices(symbol, range, granularity, limit);
      if (!csv.isEmpty()) {
        return csv;
      }
    }
    return databaseProvider.getHistoricalPrices(symbol, range, granularity, limit);
  }
}

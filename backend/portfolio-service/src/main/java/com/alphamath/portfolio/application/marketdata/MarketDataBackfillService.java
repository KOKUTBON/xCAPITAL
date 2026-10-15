package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketDataBackfillResult;
import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketPriceInput;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.domain.marketdata.PriceRange;
import com.alphamath.portfolio.infrastructure.marketdata.CsvMarketDataProvider;
import com.alphamath.portfolio.infrastructure.marketdata.HttpMarketDataProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketDataBackfillService {
  private static final int BATCH_SIZE = 5000;

  private final MarketDataService marketData;
  private final CsvMarketDataProvider csvProvider;
  private final HttpMarketDataProvider httpProvider;

  public MarketDataBackfillService(MarketDataService marketData,
                                   CsvMarketDataProvider csvProvider,
                                   HttpMarketDataProvider httpProvider) {
    this.marketData = marketData;
    this.csvProvider = csvProvider;
    this.httpProvider = httpProvider;
  }

  public MarketDataBackfillResult backfill(List<String> symbols, Instant start, Instant end,
                                           PriceGranularity granularity, int limit, String source) {
    List<String> normalized = normalizeSymbols(symbols);
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
    }

    PriceRange range = null;
    if (start != null || end != null) {
      range = new PriceRange(start == null ? Instant.EPOCH : start, end == null ? Instant.now() : end);
    }

    MarketDataProvider provider = resolveProvider(source);
    String src = source == null || source.isBlank() ? providerSource(provider) : source.trim();
    MarketDataBackfillResult result = new MarketDataBackfillResult();
    result.setSource(src);
    result.setRequestedSymbols(normalized.size());
    result.setStartedAt(Instant.now());

    Map<String, Integer> ingestedBySymbol = new LinkedHashMap<>();
    List<String> missing = new ArrayList<>();
    int ingested = 0;
    int processed = 0;

    for (String symbol : normalized) {
      List<MarketPrice> prices = provider.getHistoricalPrices(symbol, range, granularity, limit);
      if (prices.isEmpty()) {
        missing.add(symbol);
        continue;
      }
      int count = ingestPrices(src, prices);
      ingestedBySymbol.put(symbol, count);
      ingested += count;
      processed += 1;
    }

    result.setProcessedSymbols(processed);
    result.setIngestedRows(ingested);
    result.setMissingSymbols(missing);
    result.setIngestedBySymbol(ingestedBySymbol);
    result.setFinishedAt(Instant.now());
    return result;
  }

  private int ingestPrices(String source, List<MarketPrice> prices) {
    int ingested = 0;
    List<MarketPriceInput> batch = new ArrayList<>(BATCH_SIZE);
    for (MarketPrice price : prices) {
      batch.add(new MarketPriceInput(
          price.symbol(),
          price.timestamp().toString(),
          price.open(),
          price.high(),
          price.low(),
          price.close(),
          price.volume()
      ));
      if (batch.size() >= BATCH_SIZE) {
        ingested += marketData.ingest(source, batch);
        batch = new ArrayList<>(BATCH_SIZE);
      }
    }
    if (!batch.isEmpty()) {
      ingested += marketData.ingest(source, batch);
    }
    return ingested;
  }

  private List<String> normalizeSymbols(List<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      return List.of();
    }
    Map<String, Boolean> uniq = new LinkedHashMap<>();
    for (String symbol : symbols) {
      if (symbol == null || symbol.isBlank()) {
        continue;
      }
      uniq.put(symbol.trim().toUpperCase(Locale.US), true);
    }
    return new ArrayList<>(uniq.keySet());
  }

  private MarketDataProvider resolveProvider(String source) {
    String raw = source == null ? "" : source.trim();
    if (raw.isEmpty()) {
      if (httpProvider.isEnabled()) {
        return httpProvider;
      }
      if (csvProvider.isEnabled()) {
        return csvProvider;
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No backfill provider is enabled");
    }
    String normalized = raw.toLowerCase(Locale.US);
    if (matchesSource(normalized, httpProvider.getSource())) {
      if (!httpProvider.isEnabled()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HTTP provider is disabled");
      }
      return httpProvider;
    }
    if (matchesSource(normalized, csvProvider.getSource())) {
      if (!csvProvider.isEnabled()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV provider is disabled");
      }
      return csvProvider;
    }
    if (normalized.equals("db") || normalized.equals("database")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database provider is not valid for backfill");
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown market data source: " + raw);
  }

  private boolean matchesSource(String normalized, String source) {
    if (normalized == null || normalized.isBlank() || source == null || source.isBlank()) {
      return false;
    }
    return normalized.equals(source.trim().toLowerCase(Locale.US));
  }

  private String providerSource(MarketDataProvider provider) {
    if (provider == null) {
      return "unknown";
    }
    if (provider == httpProvider) {
      return httpProvider.getSource();
    }
    if (provider == csvProvider) {
      return csvProvider.getSource();
    }
    return "provider";
  }
}

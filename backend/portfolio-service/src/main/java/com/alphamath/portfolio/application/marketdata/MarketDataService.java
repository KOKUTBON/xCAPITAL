package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketPriceInput;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.domain.marketdata.PriceRange;
import com.alphamath.portfolio.infrastructure.marketdata.MarketDataQuoteCache;
import com.alphamath.portfolio.infrastructure.persistence.MarketPriceEntity;
import com.alphamath.portfolio.infrastructure.persistence.MarketPriceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketDataService {
  private final MarketPriceRepository prices;
  private final MarketDataProvider provider;
  private final MarketDataQuoteCache quoteCache;

  public MarketDataService(MarketPriceRepository prices, MarketDataProvider provider,
                           MarketDataQuoteCache quoteCache) {
    this.prices = prices;
    this.provider = provider;
    this.quoteCache = quoteCache;
  }

  public int ingest(String source, List<MarketPriceInput> input) {
    if (input == null || input.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prices cannot be empty");
    }
    String src = source == null || source.isBlank() ? "manual" : source.trim();

    List<MarketPriceEntity> batch = new ArrayList<>();
    for (MarketPriceInput price : input) {
      String symbol = normalizeSymbol(price.symbol());
      Instant ts = parseTimestamp(price.timestamp());

      validatePrice(price, symbol);

      MarketPriceEntity entity = new MarketPriceEntity();
      entity.setId(symbol + "|" + ts + "|" + src);
      entity.setSymbol(symbol);
      entity.setTs(ts);
      entity.setOpen(price.open());
      entity.setHigh(price.high());
      entity.setLow(price.low());
      entity.setClose(price.close());
      entity.setVolume(price.volume());
      entity.setSource(src);
      entity.setCreatedAt(Instant.now());
      batch.add(entity);
    }

    prices.saveAll(batch);
    return batch.size();
  }

  public List<MarketPriceEntity> listPrices(String symbol, Instant start, Instant end, int limit) {
    String sym = normalizeSymbol(symbol);
    List<MarketPriceEntity> out;
    if (start != null || end != null) {
      Instant startTs = start == null ? Instant.EPOCH : start;
      Instant endTs = end == null ? Instant.now() : end;
      out = prices.findBySymbolAndTsBetweenOrderByTsAsc(sym, startTs, endTs);
    } else {
      out = prices.findBySymbolOrderByTsAsc(sym);
    }
    if (limit > 0 && out.size() > limit) {
      return out.subList(out.size() - limit, out.size());
    }
    return out;
  }

  public List<String> symbols() {
    return prices.findDistinctSymbols();
  }

  public List<Double> returns(String symbol, Instant start, Instant end, int limit) {
    List<MarketPriceEntity> data = listPrices(symbol, start, end, limit <= 0 ? 0 : limit + 1);
    List<Double> returns = new ArrayList<>();
    for (int i = 1; i < data.size(); i++) {
      double prev = data.get(i - 1).getClose();
      double curr = data.get(i).getClose();
      if (prev <= 0.0) continue;
      returns.add((curr - prev) / prev);
    }
    return returns;
  }

  public LatestQuotesResult latestQuotes(List<String> symbols) {
    List<String> normalized = normalizeSymbols(symbols);
    Map<String, MarketQuote> cached = quoteCache.getQuotes(normalized);
    List<String> misses = new ArrayList<>();
    for (String symbol : normalized) {
      if (!cached.containsKey(symbol)) {
        misses.add(symbol);
      }
    }

    Map<String, MarketQuote> fetched = misses.isEmpty() ? Map.of() : provider.getLatestQuotes(misses);
    quoteCache.putQuotes(fetched);

    List<LatestQuotesResult.QuoteSnapshot> out = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    int cacheHits = 0;
    int fetchedCount = 0;
    for (String symbol : normalized) {
      MarketQuote quote = cached.get(symbol);
      boolean cacheHit = true;
      if (quote == null) {
        quote = fetched.get(symbol);
        cacheHit = false;
      }
      if (quote == null) {
        missing.add(symbol);
        continue;
      }
      if (cacheHit) {
        cacheHits += 1;
      } else {
        fetchedCount += 1;
      }
      out.add(new LatestQuotesResult.QuoteSnapshot(quote, cacheHit));
    }
    return new LatestQuotesResult(out, missing, cacheHits, fetchedCount);
  }

  public List<MarketPrice> historicalPrices(String symbol, Instant start, Instant end,
                                            PriceGranularity granularity, int limit) {
    String sym = normalizeSymbol(symbol);
    PriceRange range = null;
    if (start != null || end != null) {
      range = new PriceRange(start == null ? Instant.EPOCH : start, end == null ? Instant.now() : end);
    }
    return provider.getHistoricalPrices(sym, range, granularity, limit);
  }

  private String normalizeSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
    }
    return symbol.trim().toUpperCase(Locale.US);
  }

  private List<String> normalizeSymbols(List<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
    }
    Map<String, Boolean> uniq = new LinkedHashMap<>();
    for (String symbol : symbols) {
      String normalized = normalizeSymbol(symbol);
      uniq.putIfAbsent(normalized, true);
    }
    return new ArrayList<>(uniq.keySet());
  }

  private Instant parseTimestamp(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timestamp is required");
    }
    String ts = raw.trim();
    try {
      return Instant.parse(ts);
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDate.parse(ts).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timestamp must be ISO-8601");
      }
    }
  }

  private void validatePrice(MarketPriceInput price, String symbol) {
    if (!isFinite(price.open()) || !isFinite(price.high()) || !isFinite(price.low()) || !isFinite(price.close())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid price values for " + symbol);
    }
    if (price.open() <= 0 || price.high() <= 0 || price.low() <= 0 || price.close() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prices must be > 0 for " + symbol);
    }
    if (price.high() < Math.max(price.open(), price.close()) || price.low() > Math.min(price.open(), price.close())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "high/low inconsistent for " + symbol);
    }
  }

  private boolean isFinite(Double value) {
    return value != null && Double.isFinite(value);
  }

}

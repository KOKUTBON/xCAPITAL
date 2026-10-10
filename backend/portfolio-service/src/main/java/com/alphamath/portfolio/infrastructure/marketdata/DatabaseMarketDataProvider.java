package com.alphamath.portfolio.infrastructure.marketdata;

import com.alphamath.portfolio.application.marketdata.MarketDataProvider;
import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.domain.marketdata.PriceRange;
import com.alphamath.portfolio.infrastructure.persistence.MarketPriceEntity;
import com.alphamath.portfolio.infrastructure.persistence.MarketPriceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseMarketDataProvider implements MarketDataProvider {
  private final MarketPriceRepository prices;

  public DatabaseMarketDataProvider(MarketPriceRepository prices) {
    this.prices = prices;
  }

  public String getSource() {
    return "database";
  }

  @Override
  public Map<String, MarketQuote> getLatestQuotes(List<String> symbols) {
    Map<String, MarketQuote> out = new LinkedHashMap<>();
    if (symbols == null || symbols.isEmpty()) {
      return out;
    }
    for (String symbol : symbols) {
      MarketPriceEntity row = prices.findTopBySymbolOrderByTsDesc(symbol);
      if (row == null) {
        continue;
      }
      out.put(symbol, new MarketQuote(row.getSymbol(), row.getTs(), row.getClose(), row.getSource()));
    }
    return out;
  }

  @Override
  public List<MarketPrice> getHistoricalPrices(String symbol, PriceRange range,
                                               PriceGranularity granularity, int limit) {
    Instant start = range == null ? null : range.start();
    Instant end = range == null ? null : range.end();

    List<MarketPriceEntity> rows;
    if (start != null || end != null) {
      Instant startTs = start == null ? Instant.EPOCH : start;
      Instant endTs = end == null ? Instant.now() : end;
      rows = prices.findBySymbolAndTsBetweenOrderByTsAsc(symbol, startTs, endTs);
    } else {
      rows = prices.findBySymbolOrderByTsAsc(symbol);
    }

    if (rows.isEmpty()) {
      return List.of();
    }

    PriceGranularity g = granularity == null ? PriceGranularity.DAY : granularity;
    Map<Instant, MarketPrice> bucketed = new LinkedHashMap<>();
    for (MarketPriceEntity row : rows) {
      Instant bucket = row.getTs().truncatedTo(g.toChronoUnit());
      bucketed.put(bucket, toPrice(row));
    }

    List<MarketPrice> out = new ArrayList<>(bucketed.values());
    if (limit > 0 && out.size() > limit) {
      return out.subList(out.size() - limit, out.size());
    }
    return out;
  }

  private MarketPrice toPrice(MarketPriceEntity row) {
    return new MarketPrice(
        row.getSymbol(),
        row.getTs(),
        row.getOpen(),
        row.getHigh(),
        row.getLow(),
        row.getClose(),
        row.getVolume(),
        row.getSource()
    );
  }
}

package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.infrastructure.marketdata.CsvMarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class MarketDataBackfillScheduler {
  private static final Logger log = LoggerFactory.getLogger(MarketDataBackfillScheduler.class);

  private final MarketDataBackfillService backfill;
  private final MarketDataBackfillProperties properties;
  private final CsvMarketDataProvider csvProvider;

  public MarketDataBackfillScheduler(MarketDataBackfillService backfill,
                                     MarketDataBackfillProperties properties,
                                     CsvMarketDataProvider csvProvider) {
    this.backfill = backfill;
    this.properties = properties;
    this.csvProvider = csvProvider;
  }

  @Scheduled(fixedDelayString = "${alphamath.marketdata.backfill.delayMs:3600000}")
  public void run() {
    if (!properties.isEnabled()) {
      return;
    }

    List<String> symbols = properties.getSymbols();
    if ((symbols == null || symbols.isEmpty()) && isCsvSource(properties.getSource())) {
      symbols = csvProvider.listSymbols();
    }
    if (symbols == null || symbols.isEmpty()) {
      return;
    }

    Instant end = Instant.now();
    Instant start = end.minus(properties.getLookbackDays(), ChronoUnit.DAYS);
    PriceGranularity granularity = parseGranularity(properties.getGranularity());

    try {
      backfill.backfill(symbols, start, end, granularity, properties.getLimit(), properties.getSource());
    } catch (Exception e) {
      log.warn("Market data backfill failed: {}", e.getMessage());
    }
  }

  private PriceGranularity parseGranularity(String raw) {
    if (raw == null || raw.isBlank()) {
      return PriceGranularity.DAY;
    }
    try {
      return PriceGranularity.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      return PriceGranularity.DAY;
    }
  }

  private boolean isCsvSource(String source) {
    if (source == null || source.isBlank()) {
      return true;
    }
    String normalized = source.trim().toLowerCase(Locale.US);
    return normalized.equals(csvProvider.getSource().toLowerCase(Locale.US)) || normalized.equals("csv");
  }
}

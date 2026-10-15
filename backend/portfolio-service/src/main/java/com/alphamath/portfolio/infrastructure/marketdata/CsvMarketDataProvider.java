package com.alphamath.portfolio.infrastructure.marketdata;

import com.alphamath.portfolio.application.marketdata.MarketDataProvider;
import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.domain.marketdata.PriceRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CsvMarketDataProvider implements MarketDataProvider {
  private static final Logger log = LoggerFactory.getLogger(CsvMarketDataProvider.class);

  private final MarketDataCsvProperties properties;

  public CsvMarketDataProvider(MarketDataCsvProperties properties) {
    this.properties = properties;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public String getSource() {
    return properties.getSource() == null || properties.getSource().isBlank()
        ? "csv" : properties.getSource().trim();
  }

  public String getDirectory() {
    return properties.getDirectory();
  }

  public List<String> listSymbols() {
    Path dir = resolveDirectory();
    if (dir == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    try (var stream = Files.list(dir)) {
      stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.US).endsWith(".csv"))
          .forEach(p -> {
            String name = p.getFileName().toString();
            int idx = name.lastIndexOf('.');
            if (idx > 0) {
              out.add(name.substring(0, idx).toUpperCase(Locale.US));
            }
          });
    } catch (IOException e) {
      log.warn("CSV provider list failed: {}", e.getMessage());
      return List.of();
    }
    return out;
  }

  @Override
  public Map<String, MarketQuote> getLatestQuotes(List<String> symbols) {
    if (!isEnabled()) {
      return Map.of();
    }
    Map<String, MarketQuote> out = new LinkedHashMap<>();
    if (symbols == null || symbols.isEmpty()) {
      return out;
    }
    for (String symbol : symbols) {
      MarketPrice latest = latestPrice(symbol);
      if (latest != null) {
        out.put(latest.symbol(), new MarketQuote(latest.symbol(), latest.timestamp(), latest.close(), latest.source()));
      }
    }
    return out;
  }

  @Override
  public List<MarketPrice> getHistoricalPrices(String symbol, PriceRange range,
                                               PriceGranularity granularity, int limit) {
    if (!isEnabled()) {
      return List.of();
    }
    Path file = resolveSymbolFile(symbol);
    if (file == null) {
      return List.of();
    }
    List<MarketPrice> rows = readPrices(file, symbol);
    if (rows.isEmpty()) {
      return List.of();
    }

    Instant start = range == null ? null : range.start();
    Instant end = range == null ? null : range.end();
    List<MarketPrice> filtered = new ArrayList<>();
    for (MarketPrice row : rows) {
      Instant ts = row.timestamp();
      if (start != null && ts.isBefore(start)) {
        continue;
      }
      if (end != null && ts.isAfter(end)) {
        continue;
      }
      filtered.add(row);
    }

    if (filtered.isEmpty()) {
      return List.of();
    }

    filtered.sort(Comparator.comparing(MarketPrice::timestamp));
    PriceGranularity g = granularity == null ? PriceGranularity.DAY : granularity;
    Map<Instant, MarketPrice> bucketed = new LinkedHashMap<>();
    for (MarketPrice row : filtered) {
      Instant bucket = row.timestamp().truncatedTo(g.toChronoUnit());
      bucketed.put(bucket, row);
    }

    List<MarketPrice> out = new ArrayList<>(bucketed.values());
    if (limit > 0 && out.size() > limit) {
      return out.subList(out.size() - limit, out.size());
    }
    return out;
  }

  private MarketPrice latestPrice(String symbol) {
    Path file = resolveSymbolFile(symbol);
    if (file == null) {
      return null;
    }
    CsvHeader header = null;
    Instant latestTs = null;
    MarketPrice latest = null;
    int lineNo = 0;
    String defaultSymbol = normalizeSymbol(symbol);
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        lineNo++;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        if (header == null) {
          CsvHeader parsed = CsvHeader.tryParse(trimmed);
          if (parsed != null) {
            header = parsed;
            continue;
          }
          header = CsvHeader.defaultHeader();
        }
        CsvRow row = parseRow(trimmed, header, defaultSymbol);
        if (row == null) {
          continue;
        }
        if (latestTs == null || row.timestamp.isAfter(latestTs)) {
          latestTs = row.timestamp;
          latest = row.toMarketPrice();
        }
      }
    } catch (IOException e) {
      log.warn("CSV provider read failed: {}", e.getMessage());
      return null;
    } catch (RuntimeException e) {
      log.warn("CSV provider parse failed ({}:{}): {}", file.getFileName(), lineNo, e.getMessage());
      return null;
    }
    return latest;
  }

  private List<MarketPrice> readPrices(Path file, String symbol) {
    List<MarketPrice> out = new ArrayList<>();
    CsvHeader header = null;
    int lineNo = 0;
    String defaultSymbol = normalizeSymbol(symbol);
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        lineNo++;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        if (header == null) {
          CsvHeader parsed = CsvHeader.tryParse(trimmed);
          if (parsed != null) {
            header = parsed;
            continue;
          }
          header = CsvHeader.defaultHeader();
        }
        CsvRow row = parseRow(trimmed, header, defaultSymbol);
        if (row == null) {
          continue;
        }
        out.add(row.toMarketPrice());
      }
    } catch (IOException e) {
      log.warn("CSV provider read failed: {}", e.getMessage());
      return List.of();
    } catch (RuntimeException e) {
      log.warn("CSV provider parse failed ({}:{}): {}", file.getFileName(), lineNo, e.getMessage());
      return List.of();
    }
    return out;
  }

  private CsvRow parseRow(String line, CsvHeader header, String defaultSymbol) {
    String[] parts = split(line);
    if (parts.length < header.minColumns) {
      return null;
    }
    String symbol = header.symbolIndex >= 0 && header.symbolIndex < parts.length
        ? parts[header.symbolIndex].trim() : defaultSymbol;
    if (symbol == null || symbol.isBlank()) {
      symbol = defaultSymbol;
    }
    String source = header.sourceIndex >= 0 && header.sourceIndex < parts.length
        ? parts[header.sourceIndex].trim() : getSource();
    if (source == null || source.isBlank()) {
      source = getSource();
    }

    String tsRaw = parts[header.timestampIndex].trim();
    Instant ts = parseTimestamp(tsRaw);
    double open = parseDouble(parts[header.openIndex]);
    double high = parseDouble(parts[header.highIndex]);
    double low = parseDouble(parts[header.lowIndex]);
    double close = parseDouble(parts[header.closeIndex]);
    Double volume = null;
    if (header.volumeIndex >= 0 && header.volumeIndex < parts.length) {
      String vRaw = parts[header.volumeIndex].trim();
      if (!vRaw.isEmpty()) {
        volume = parseDouble(vRaw);
      }
    }

    return new CsvRow(symbol, ts, open, high, low, close, volume, source);
  }

  private String[] split(String line) {
    return line.split(",");
  }

  private Instant parseTimestamp(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("timestamp is required");
    }
    String ts = raw.trim();
    try {
      return Instant.parse(ts);
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDate.parse(ts).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException("timestamp must be ISO-8601");
      }
    }
  }

  private double parseDouble(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("numeric value missing");
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("numeric value missing");
    }
    return Double.parseDouble(trimmed);
  }

  private Path resolveSymbolFile(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    Path dir = resolveDirectory();
    if (dir == null) {
      return null;
    }
    String normalized = normalizeSymbol(symbol);
    Path candidate = dir.resolve(normalized + ".csv");
    if (Files.exists(candidate)) {
      return candidate;
    }
    return null;
  }

  private Path resolveDirectory() {
    if (!isEnabled()) {
      return null;
    }
    String raw = properties.getDirectory();
    if (raw == null || raw.isBlank()) {
      return null;
    }
    Path dir = Paths.get(raw).toAbsolutePath().normalize();
    if (!Files.exists(dir) || !Files.isDirectory(dir)) {
      return null;
    }
    return dir;
  }

  private String normalizeSymbol(String symbol) {
    return symbol.trim().toUpperCase(Locale.US);
  }

  private static class CsvHeader {
    final int timestampIndex;
    final int openIndex;
    final int highIndex;
    final int lowIndex;
    final int closeIndex;
    final int volumeIndex;
    final int symbolIndex;
    final int sourceIndex;
    final int minColumns;

    private CsvHeader(int timestampIndex, int openIndex, int highIndex, int lowIndex,
                      int closeIndex, int volumeIndex, int symbolIndex, int sourceIndex, int minColumns) {
      this.timestampIndex = timestampIndex;
      this.openIndex = openIndex;
      this.highIndex = highIndex;
      this.lowIndex = lowIndex;
      this.closeIndex = closeIndex;
      this.volumeIndex = volumeIndex;
      this.symbolIndex = symbolIndex;
      this.sourceIndex = sourceIndex;
      this.minColumns = minColumns;
    }

    static CsvHeader defaultHeader() {
      return new CsvHeader(0, 1, 2, 3, 4, 5, -1, -1, 5);
    }

    static CsvHeader tryParse(String line) {
      String lower = line.toLowerCase(Locale.US);
      if (!lower.contains("timestamp") && !lower.contains("open") && !lower.contains("close") && !lower.contains("date")) {
        return null;
      }
      String[] cols = line.split(",");
      Map<String, Integer> index = new LinkedHashMap<>();
      for (int i = 0; i < cols.length; i++) {
        String key = cols[i].trim().toLowerCase(Locale.US);
        if (key.equals("date")) {
          key = "timestamp";
        }
        index.put(key, i);
      }
      int ts = index.getOrDefault("timestamp", -1);
      int open = index.getOrDefault("open", -1);
      int high = index.getOrDefault("high", -1);
      int low = index.getOrDefault("low", -1);
      int close = index.getOrDefault("close", -1);
      int volume = index.getOrDefault("volume", -1);
      int symbol = index.getOrDefault("symbol", -1);
      int source = index.getOrDefault("source", -1);
      if (ts < 0 || open < 0 || high < 0 || low < 0 || close < 0) {
        return null;
      }
      int min = Math.max(Math.max(Math.max(Math.max(ts, open), Math.max(high, low)), close), 0) + 1;
      return new CsvHeader(ts, open, high, low, close, volume, symbol, source, min);
    }
  }

  private static class CsvRow {
    final String symbol;
    final Instant timestamp;
    final double open;
    final double high;
    final double low;
    final double close;
    final Double volume;
    final String source;

    CsvRow(String symbol, Instant timestamp, double open, double high, double low,
           double close, Double volume, String source) {
      this.symbol = symbol;
      this.timestamp = timestamp;
      this.open = open;
      this.high = high;
      this.low = low;
      this.close = close;
      this.volume = volume;
      this.source = source;
    }

    MarketPrice toMarketPrice() {
      return new MarketPrice(symbol, timestamp, open, high, low, close, volume, source);
    }
  }
}

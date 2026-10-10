package com.alphamath.portfolio.infrastructure.marketdata;

import com.alphamath.portfolio.application.marketdata.MarketDataProvider;
import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.domain.marketdata.PriceRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
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
public class HttpMarketDataProvider implements MarketDataProvider {
  private static final Logger log = LoggerFactory.getLogger(HttpMarketDataProvider.class);

  private final MarketDataHttpProperties properties;
  private final RestTemplate restTemplate;
  private final MarketDataRateLimiter rateLimiter;
  private final ObjectMapper mapper;

  public HttpMarketDataProvider(MarketDataHttpProperties properties,
                                RestTemplateBuilder builder,
                                ObjectMapper mapper) {
    this.properties = properties;
    Duration connectTimeout = Duration.ofMillis(Math.max(100, properties.getConnectTimeoutMs()));
    Duration readTimeout = Duration.ofMillis(Math.max(100, properties.getReadTimeoutMs()));
    this.restTemplate = builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
    this.rateLimiter = new MarketDataRateLimiter(properties.getRateLimitPerMinute());
    this.mapper = mapper;
  }

  public boolean isEnabled() {
    String baseUrl = properties.getBaseUrl();
    return properties.isEnabled() && baseUrl != null && !baseUrl.isBlank();
  }

  public String getSource() {
    String source = properties.getSource();
    return source == null || source.isBlank() ? "http" : source.trim();
  }

  public String getBaseUrl() {
    return properties.getBaseUrl();
  }

  public int getMaxSymbolsPerRequest() {
    return Math.max(1, properties.getMaxSymbolsPerRequest());
  }

  public int getRateLimitPerMinute() {
    return properties.getRateLimitPerMinute();
  }

  @Override
  public Map<String, MarketQuote> getLatestQuotes(List<String> symbols) {
    Map<String, MarketQuote> out = new LinkedHashMap<>();
    if (!isEnabled() || symbols == null || symbols.isEmpty()) {
      return out;
    }
    List<List<String>> batches = chunkSymbols(symbols, getMaxSymbolsPerRequest());
    for (List<String> batch : batches) {
      JsonNode response = requestLatestQuotes(batch);
      if (response == null || response.isMissingNode()) {
        continue;
      }
      for (MarketQuote parsed : parseQuotes(response)) {
        out.put(parsed.symbol(), parsed);
      }
    }
    return out;
  }

  @Override
  public List<MarketPrice> getHistoricalPrices(String symbol, PriceRange range,
                                               PriceGranularity granularity, int limit) {
    if (!isEnabled() || symbol == null || symbol.isBlank()) {
      return List.of();
    }
    String normalized = normalizeSymbol(symbol);
    JsonNode response = requestHistory(normalized, range, granularity, limit);
    if (response == null || response.isMissingNode()) {
      return List.of();
    }

    Instant start = range == null ? null : range.start();
    Instant end = range == null ? null : range.end();
    List<MarketPrice> out = new ArrayList<>();
    for (MarketPrice parsed : parsePrices(response, normalized)) {
      Instant ts = parsed.timestamp();
      if (start != null && ts.isBefore(start)) {
        continue;
      }
      if (end != null && ts.isAfter(end)) {
        continue;
      }
      out.add(parsed);
    }
    if (out.isEmpty()) {
      return List.of();
    }
    out.sort(Comparator.comparing(MarketPrice::timestamp));
    if (limit > 0 && out.size() > limit) {
      return out.subList(out.size() - limit, out.size());
    }
    return out;
  }

  private JsonNode requestLatestQuotes(List<String> symbols) {
    if (!rateLimiter.tryAcquire()) {
      log.warn("HTTP market data rate limit reached");
      return mapper.createObjectNode();
    }
    String url = buildUrl(properties.getLatestQuotesPath());
    if (url.isBlank()) {
      return mapper.createObjectNode();
    }
    String joined = String.join(",", symbols);
    String uri = UriComponentsBuilder.fromHttpUrl(url)
        .queryParam("symbols", joined)
        .toUriString();
    try {
      ResponseEntity<String> response = restTemplate.exchange(
          uri, HttpMethod.GET, buildHeaders(), String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        log.warn("HTTP market data latest quotes failed: {}", response.getStatusCode());
        return mapper.createObjectNode();
      }
      String body = response.getBody() == null ? "{}" : response.getBody();
      return mapper.readTree(body);
    } catch (RestClientException e) {
      log.warn("HTTP market data latest quotes failed: {}", e.getMessage());
      return mapper.createObjectNode();
    } catch (Exception e) {
      return mapper.createObjectNode();
    }
  }

  private JsonNode requestHistory(String symbol, PriceRange range,
                                  PriceGranularity granularity, int limit) {
    if (!rateLimiter.tryAcquire()) {
      log.warn("HTTP market data rate limit reached");
      return mapper.createObjectNode();
    }
    String url = buildUrl(properties.getHistoryPath());
    if (url.isBlank()) {
      return mapper.createObjectNode();
    }
    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
        .queryParam("symbol", symbol);
    if (range != null) {
      if (range.start() != null) {
        builder.queryParam("start", range.start().toString());
      }
      if (range.end() != null) {
        builder.queryParam("end", range.end().toString());
      }
    }
    if (granularity != null) {
      builder.queryParam("granularity", granularity.name());
    }
    if (limit > 0) {
      builder.queryParam("limit", limit);
    }
    try {
      ResponseEntity<String> response = restTemplate.exchange(
          builder.toUriString(), HttpMethod.GET, buildHeaders(), String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        log.warn("HTTP market data history failed: {}", response.getStatusCode());
        return mapper.createObjectNode();
      }
      String body = response.getBody() == null ? "{}" : response.getBody();
      return mapper.readTree(body);
    } catch (RestClientException e) {
      log.warn("HTTP market data history failed: {}", e.getMessage());
      return mapper.createObjectNode();
    } catch (Exception e) {
      return mapper.createObjectNode();
    }
  }

  private HttpEntity<Void> buildHeaders() {
    HttpHeaders headers = new HttpHeaders();
    String key = properties.getApiKey();
    String header = properties.getApiKeyHeader();
    if (header != null && !header.isBlank() && key != null && !key.isBlank()) {
      headers.set(header.trim(), key.trim());
    }
    return new HttpEntity<>(headers);
  }

  private String buildUrl(String path) {
    String baseUrl = properties.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      return "";
    }
    String base = baseUrl.trim();
    String suffix = path == null ? "" : path.trim();
    if (suffix.isEmpty()) {
      return base;
    }
    if (base.endsWith("/") && suffix.startsWith("/")) {
      return base.substring(0, base.length() - 1) + suffix;
    }
    if (!base.endsWith("/") && !suffix.startsWith("/")) {
      return base + "/" + suffix;
    }
    return base + suffix;
  }

  private List<List<String>> chunkSymbols(List<String> symbols, int maxSymbols) {
    List<List<String>> out = new ArrayList<>();
    if (symbols == null || symbols.isEmpty()) {
      return out;
    }
    int safeMax = Math.max(1, maxSymbols);
    List<String> current = new ArrayList<>(safeMax);
    for (String symbol : symbols) {
      if (symbol == null || symbol.isBlank()) {
        continue;
      }
      current.add(symbol);
      if (current.size() >= safeMax) {
        out.add(current);
        current = new ArrayList<>(safeMax);
      }
    }
    if (!current.isEmpty()) {
      out.add(current);
    }
    return out;
  }

  private List<MarketQuote> parseQuotes(JsonNode root) {
    List<MarketQuote> out = new ArrayList<>();
    MarketDataHttpProperties.Mapping mapping = properties.getMapping();
    JsonNode quotes = nodeAt(root, mapping.getLatestQuotesPointer());
    if (quotes == null || !quotes.isArray()) {
      return out;
    }
    MarketDataHttpProperties.QuoteMapping qmap = mapping.getQuote();
    for (JsonNode node : quotes) {
      String symbol = normalizeSymbol(text(nodeAt(node, qmap.getSymbol())));
      if (symbol == null) {
        continue;
      }
      Instant ts = parseTimestamp(nodeAt(node, qmap.getTimestamp()));
      if (ts == null) {
        continue;
      }
      Double price = readDouble(nodeAt(node, qmap.getPrice()));
      if (!isFinite(price) || price <= 0.0) {
        continue;
      }
      String source = text(nodeAt(node, qmap.getSource()));
      if (source == null || source.isBlank()) {
        source = getSource();
      }
      out.add(new MarketQuote(symbol, ts, price, source));
    }
    return out;
  }

  private List<MarketPrice> parsePrices(JsonNode root, String fallbackSymbol) {
    List<MarketPrice> out = new ArrayList<>();
    MarketDataHttpProperties.Mapping mapping = properties.getMapping();
    JsonNode prices = nodeAt(root, mapping.getHistoryPricesPointer());
    if (prices == null || !prices.isArray()) {
      return out;
    }
    MarketDataHttpProperties.PriceMapping pmap = mapping.getPrice();
    for (JsonNode node : prices) {
      String symbol = normalizeSymbol(text(nodeAt(node, pmap.getSymbol())));
      if (symbol == null) {
        symbol = fallbackSymbol;
      }
      Instant ts = parseTimestamp(nodeAt(node, pmap.getTimestamp()));
      if (ts == null) {
        continue;
      }
      Double open = readDouble(nodeAt(node, pmap.getOpen()));
      Double high = readDouble(nodeAt(node, pmap.getHigh()));
      Double low = readDouble(nodeAt(node, pmap.getLow()));
      Double close = readDouble(nodeAt(node, pmap.getClose()));
      if (!isFinite(open) || !isFinite(high) || !isFinite(low) || !isFinite(close)) {
        continue;
      }
      if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
        continue;
      }
      if (high < Math.max(open, close) || low > Math.min(open, close)) {
        continue;
      }
      Double volume = readDouble(nodeAt(node, pmap.getVolume()));
      String source = text(nodeAt(node, pmap.getSource()));
      if (source == null || source.isBlank()) {
        source = getSource();
      }
      out.add(new MarketPrice(symbol, ts, open, high, low, close, volume, source));
    }
    return out;
  }

  private String normalizeSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    return symbol.trim().toUpperCase(Locale.US);
  }

  private Instant parseTimestamp(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String ts = raw.trim();
    try {
      return Instant.parse(ts);
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDate.parse(ts).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e) {
        return null;
      }
    }
  }

  private boolean isFinite(Double value) {
    return value != null && Double.isFinite(value);
  }

  private Instant parseTimestamp(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isNumber()) {
      long value = node.asLong();
      if (value > 100000000000L) {
        return Instant.ofEpochMilli(value);
      }
      return Instant.ofEpochSecond(value);
    }
    if (node.isTextual()) {
      return parseTimestamp(node.asText());
    }
    return null;
  }

  private JsonNode nodeAt(JsonNode node, String pointer) {
    if (node == null) {
      return mapper.createObjectNode();
    }
    if (pointer == null || pointer.isBlank()) {
      return node;
    }
    if (pointer.startsWith("/")) {
      return node.at(pointer);
    }
    return node.path(pointer);
  }

  private String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    if (node.isNumber()) {
      return node.asText();
    }
    return null;
  }

  private Double readDouble(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isNumber()) {
      return node.asDouble();
    }
    if (node.isTextual()) {
      try {
        return Double.parseDouble(node.asText());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}

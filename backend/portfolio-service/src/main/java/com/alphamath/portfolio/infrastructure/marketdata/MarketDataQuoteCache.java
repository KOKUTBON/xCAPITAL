package com.alphamath.portfolio.infrastructure.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketDataQuoteCache {
  private static final Logger log = LoggerFactory.getLogger(MarketDataQuoteCache.class);
  private static final String KEY_PREFIX = "market:quote:";

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final Duration ttl;

  public MarketDataQuoteCache(StringRedisTemplate redis,
                              ObjectMapper mapper,
                              @Value("${alphamath.marketdata.quoteCacheTtlSeconds:30}") long ttlSeconds) {
    this.redis = redis;
    this.mapper = mapper;
    long safeTtl = Math.max(1, ttlSeconds);
    this.ttl = Duration.ofSeconds(safeTtl);
  }

  public Map<String, MarketQuote> getQuotes(List<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      return Map.of();
    }
    try {
      List<String> keys = symbols.stream().map(this::key).toList();
      List<String> values = redis.opsForValue().multiGet(keys);
      if (values == null || values.isEmpty()) {
        return Map.of();
      }
      Map<String, MarketQuote> out = new LinkedHashMap<>();
      for (int i = 0; i < symbols.size() && i < values.size(); i++) {
        String raw = values.get(i);
        if (raw == null || raw.isBlank()) {
          continue;
        }
        try {
          MarketQuote quote = mapper.readValue(raw, MarketQuote.class);
          out.put(symbols.get(i), quote);
        } catch (Exception e) {
          log.debug("Invalid cached quote for {}: {}", symbols.get(i), e.getMessage());
        }
      }
      return out;
    } catch (Exception e) {
      log.warn("Quote cache read failed: {}", e.getMessage());
      return Map.of();
    }
  }

  public void putQuotes(Map<String, MarketQuote> quotes) {
    if (quotes == null || quotes.isEmpty()) {
      return;
    }
    try {
      for (Map.Entry<String, MarketQuote> entry : quotes.entrySet()) {
        String payload = mapper.writeValueAsString(entry.getValue());
        redis.opsForValue().set(key(entry.getKey()), payload, ttl);
      }
    } catch (Exception e) {
      log.warn("Quote cache write failed: {}", e.getMessage());
    }
  }

  private String key(String symbol) {
    return KEY_PREFIX + symbol;
  }
}

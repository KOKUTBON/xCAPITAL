package com.alphamath.portfolio.infrastructure.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.marketdata.http")
public class MarketDataHttpProperties {
  private boolean enabled = false;
  private String baseUrl = "";
  private String latestQuotesPath = "/quotes/latest";
  private String historyPath = "/history";
  private String apiKeyHeader = "X-API-KEY";
  private String apiKey = "";
  private int connectTimeoutMs = 2000;
  private int readTimeoutMs = 4000;
  private int maxSymbolsPerRequest = 50;
  private int rateLimitPerMinute = 120;
  private String source = "http";
  private Mapping mapping = new Mapping();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getLatestQuotesPath() {
    return latestQuotesPath;
  }

  public void setLatestQuotesPath(String latestQuotesPath) {
    this.latestQuotesPath = latestQuotesPath;
  }

  public String getHistoryPath() {
    return historyPath;
  }

  public void setHistoryPath(String historyPath) {
    this.historyPath = historyPath;
  }

  public String getApiKeyHeader() {
    return apiKeyHeader;
  }

  public void setApiKeyHeader(String apiKeyHeader) {
    this.apiKeyHeader = apiKeyHeader;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }

  public int getMaxSymbolsPerRequest() {
    return maxSymbolsPerRequest;
  }

  public void setMaxSymbolsPerRequest(int maxSymbolsPerRequest) {
    this.maxSymbolsPerRequest = maxSymbolsPerRequest;
  }

  public int getRateLimitPerMinute() {
    return rateLimitPerMinute;
  }

  public void setRateLimitPerMinute(int rateLimitPerMinute) {
    this.rateLimitPerMinute = rateLimitPerMinute;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Mapping getMapping() {
    return mapping;
  }

  public void setMapping(Mapping mapping) {
    this.mapping = mapping == null ? new Mapping() : mapping;
  }

  public static class Mapping {
    private String latestQuotesPointer = "/quotes";
    private String historyPricesPointer = "/prices";
    private QuoteMapping quote = new QuoteMapping();
    private PriceMapping price = new PriceMapping();

    public String getLatestQuotesPointer() {
      return latestQuotesPointer;
    }

    public void setLatestQuotesPointer(String latestQuotesPointer) {
      this.latestQuotesPointer = latestQuotesPointer;
    }

    public String getHistoryPricesPointer() {
      return historyPricesPointer;
    }

    public void setHistoryPricesPointer(String historyPricesPointer) {
      this.historyPricesPointer = historyPricesPointer;
    }

    public QuoteMapping getQuote() {
      return quote;
    }

    public void setQuote(QuoteMapping quote) {
      this.quote = quote == null ? new QuoteMapping() : quote;
    }

    public PriceMapping getPrice() {
      return price;
    }

    public void setPrice(PriceMapping price) {
      this.price = price == null ? new PriceMapping() : price;
    }
  }

  public static class QuoteMapping {
    private String symbol = "/symbol";
    private String timestamp = "/timestamp";
    private String price = "/price";
    private String source = "/source";

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public String getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(String timestamp) {
      this.timestamp = timestamp;
    }

    public String getPrice() {
      return price;
    }

    public void setPrice(String price) {
      this.price = price;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }

  public static class PriceMapping {
    private String symbol = "/symbol";
    private String timestamp = "/timestamp";
    private String open = "/open";
    private String high = "/high";
    private String low = "/low";
    private String close = "/close";
    private String volume = "/volume";
    private String source = "/source";

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public String getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(String timestamp) {
      this.timestamp = timestamp;
    }

    public String getOpen() {
      return open;
    }

    public void setOpen(String open) {
      this.open = open;
    }

    public String getHigh() {
      return high;
    }

    public void setHigh(String high) {
      this.high = high;
    }

    public String getLow() {
      return low;
    }

    public void setLow(String low) {
      this.low = low;
    }

    public String getClose() {
      return close;
    }

    public void setClose(String close) {
      this.close = close;
    }

    public String getVolume() {
      return volume;
    }

    public void setVolume(String volume) {
      this.volume = volume;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }
}

package com.alphamath.portfolio.application.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "alphamath.marketdata.backfill")
public class MarketDataBackfillProperties {
  private boolean enabled = false;
  private List<String> symbols = new ArrayList<>();
  private int lookbackDays = 365;
  private String granularity = "DAY";
  private String source = "csv";
  private int limit = 0;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public List<String> getSymbols() {
    return symbols;
  }

  public void setSymbols(List<String> symbols) {
    this.symbols = symbols;
  }

  public int getLookbackDays() {
    return lookbackDays;
  }

  public void setLookbackDays(int lookbackDays) {
    this.lookbackDays = lookbackDays;
  }

  public String getGranularity() {
    return granularity;
  }

  public void setGranularity(String granularity) {
    this.granularity = granularity;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }
}

package com.alphamath.portfolio.infrastructure.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.marketdata.csv")
public class MarketDataCsvProperties {
  private boolean enabled = true;
  private String directory = "./data/market";
  private String source = "csv";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}

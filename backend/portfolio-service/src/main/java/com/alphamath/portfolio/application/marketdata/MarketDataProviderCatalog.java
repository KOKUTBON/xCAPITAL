package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.infrastructure.marketdata.CsvMarketDataProvider;
import com.alphamath.portfolio.infrastructure.marketdata.DatabaseMarketDataProvider;
import com.alphamath.portfolio.infrastructure.marketdata.HttpMarketDataProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketDataProviderCatalog {
  private final CsvMarketDataProvider csvProvider;
  private final DatabaseMarketDataProvider databaseProvider;
  private final HttpMarketDataProvider httpProvider;

  public MarketDataProviderCatalog(CsvMarketDataProvider csvProvider,
                                   DatabaseMarketDataProvider databaseProvider,
                                   HttpMarketDataProvider httpProvider) {
    this.csvProvider = csvProvider;
    this.databaseProvider = databaseProvider;
    this.httpProvider = httpProvider;
  }

  public List<MarketDataProviderSnapshot> listProviders() {
    List<MarketDataProviderSnapshot> out = new ArrayList<>();
    out.add(new MarketDataProviderSnapshot(
        "http",
        httpProvider.getSource(),
        httpProvider.isEnabled(),
        "http",
        sanitizeUrl(httpProvider.getBaseUrl()),
        httpProvider.getMaxSymbolsPerRequest(),
        httpProvider.getRateLimitPerMinute()
    ));
    out.add(new MarketDataProviderSnapshot(
        "csv",
        csvProvider.getSource(),
        csvProvider.isEnabled(),
        "csv",
        csvProvider.getDirectory(),
        null,
        null
    ));
    out.add(new MarketDataProviderSnapshot(
        "database",
        databaseProvider.getSource(),
        true,
        "database",
        "postgres",
        null,
        null
    ));
    return out;
  }

  private String sanitizeUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(raw.trim());
      String scheme = uri.getScheme();
      String host = uri.getHost();
      int port = uri.getPort();
      if (host == null || host.isBlank()) {
        return raw.trim();
      }
      StringBuilder out = new StringBuilder();
      if (scheme != null && !scheme.isBlank()) {
        out.append(scheme).append("://");
      }
      out.append(host);
      if (port > 0) {
        out.append(":").append(port);
      }
      return out.toString();
    } catch (Exception ignored) {
      return raw.trim();
    }
  }

  public record MarketDataProviderSnapshot(
      String name,
      String source,
      boolean enabled,
      String kind,
      String location,
      Integer maxSymbolsPerRequest,
      Integer rateLimitPerMinute
  ) {}
}

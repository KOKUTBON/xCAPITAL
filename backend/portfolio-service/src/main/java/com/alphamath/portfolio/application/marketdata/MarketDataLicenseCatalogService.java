package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketDataLicenseCatalogEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarketDataLicenseCatalogService {
  private final List<MarketDataLicenseCatalogProvider> providers;

  public MarketDataLicenseCatalogService(List<MarketDataLicenseCatalogProvider> providers) {
    this.providers = providers == null ? List.of() : providers;
  }

  public List<MarketDataLicenseCatalogEntry> listCatalog() {
    List<MarketDataLicenseCatalogEntry> out = new ArrayList<>();
    for (MarketDataLicenseCatalogProvider provider : providers) {
      if (provider.enabled()) {
        out.addAll(provider.listCatalog());
      }
    }
    return out;
  }
}

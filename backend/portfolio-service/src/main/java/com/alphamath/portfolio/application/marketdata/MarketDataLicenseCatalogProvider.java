package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketDataLicenseCatalogEntry;

import java.util.List;

public interface MarketDataLicenseCatalogProvider {
  boolean enabled();

  List<MarketDataLicenseCatalogEntry> listCatalog();
}

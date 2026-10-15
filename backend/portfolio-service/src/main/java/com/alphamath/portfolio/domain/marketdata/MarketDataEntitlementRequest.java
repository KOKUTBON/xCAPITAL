package com.alphamath.portfolio.domain.marketdata;

import lombok.Data;

@Data
public class MarketDataEntitlementRequest {
  private String id;
  private MarketDataEntitlementType entitlementType;
  private String entitlementValue;
  private String status;
  private String source;
}

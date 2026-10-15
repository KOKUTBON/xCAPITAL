package com.alphamath.portfolio.domain.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class MarketDataEntitlement {
  private String id;
  @JsonIgnore
  private String userId;
  private MarketDataEntitlementType entitlementType;
  private String entitlementValue;
  private MarketDataEntitlementStatus status;
  private String source;
  private Instant createdAt;
  private Instant updatedAt;
}

package com.alphamath.portfolio.domain.funding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class FundingSource {
  private String id;
  @JsonIgnore
  private String userId;
  private FundingMethodType methodType;
  private String providerId;
  private String providerReference;
  private String label;
  private String last4;
  private String currency;
  private String network;
  private FundingStatus status;
  private Instant createdAt;
}

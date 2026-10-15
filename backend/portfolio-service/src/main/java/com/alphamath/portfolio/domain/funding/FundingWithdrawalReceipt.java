package com.alphamath.portfolio.domain.funding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class FundingWithdrawalReceipt {
  private String id;
  private String sourceId;
  private double amount;
  private String currency;
  private String status;
  private String note;
  private String providerId;
  private String providerReference;
  private Instant createdAt;
  private Instant updatedAt;
  @JsonIgnore
  private String userId;
}

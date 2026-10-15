package com.alphamath.portfolio.domain.funding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FundingLinkRequest {
  @NotNull
  private FundingMethodType methodType;

  @NotBlank
  private String providerId;

  @NotBlank
  private String label;

  private String last4;
  private String currency = "USD";
  private String network;
  private String providerReference;
}

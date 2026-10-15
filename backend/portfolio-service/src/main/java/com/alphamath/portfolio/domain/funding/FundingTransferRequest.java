package com.alphamath.portfolio.domain.funding;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FundingTransferRequest {
  @NotBlank
  private String sourceId;

  @NotBlank
  private String brokerAccountId;

  @NotNull
  private FundingTransferDirection direction;

  @DecimalMin("0.01")
  private Double amount;

  private String currency;
  private String reference;
}

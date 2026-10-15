package com.alphamath.portfolio.domain.funding;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FundingWithdrawalRequest {
  @NotBlank
  private String sourceId;

  @DecimalMin("0.01")
  private Double amount;

  private String currency;
  private String destination;
  private String memo;
}

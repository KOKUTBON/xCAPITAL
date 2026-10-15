package com.alphamath.portfolio.domain.banking;

import lombok.Data;

@Data
public class BankingTransferRequest {
  private BankingTransferDirection direction;
  private Double amount;
  private String currency;
  private String note;
}

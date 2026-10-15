package com.alphamath.portfolio.domain.banking;

import lombok.Data;

import java.time.Instant;

@Data
public class BankingTransfer {
  private String id;
  private String userId;
  private BankingTransferDirection direction;
  private double amount;
  private String currency;
  private BankingTransferStatus status;
  private String note;
  private Instant createdAt;
}

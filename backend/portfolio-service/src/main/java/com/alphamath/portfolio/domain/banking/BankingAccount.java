package com.alphamath.portfolio.domain.banking;

import lombok.Data;

import java.time.Instant;

@Data
public class BankingAccount {
  private String id;
  private String userId;
  private String status;
  private double cash;
  private String currency;
  private Instant createdAt;
  private Instant updatedAt;
}

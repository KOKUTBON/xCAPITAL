package com.alphamath.portfolio.domain.rewards;

import lombok.Data;

import java.time.Instant;

@Data
public class RewardOffer {
  private String id;
  private String name;
  private String description;
  private double minDeposit;
  private double bonusAmount;
  private String currency;
  private String status;
  private Instant createdAt;
}

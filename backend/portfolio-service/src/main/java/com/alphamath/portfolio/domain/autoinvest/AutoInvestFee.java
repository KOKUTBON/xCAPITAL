package com.alphamath.portfolio.domain.autoinvest;

import lombok.Data;

import java.time.Instant;

@Data
public class AutoInvestFee {
  private String id;
  private String planId;
  private String userId;
  private double amount;
  private double equity;
  private double feeBpsAnnual;
  private int chargeDays;
  private String status;
  private Instant createdAt;
}

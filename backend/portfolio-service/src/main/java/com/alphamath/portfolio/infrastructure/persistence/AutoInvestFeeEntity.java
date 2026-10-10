package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_auto_invest_fee")
@Data
public class AutoInvestFeeEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String planId;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private double amount;

  @Column(nullable = false)
  private double equity;

  @Column(nullable = false)
  private double feeBpsAnnual;

  @Column(nullable = false)
  private int chargeDays;

  @Column(nullable = false)
  private String status;

  private Instant createdAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_reward_offer")
@Data
public class RewardOfferEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(nullable = false)
  private double minDeposit;

  @Column(nullable = false)
  private double bonusAmount;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private String status;

  private Instant createdAt;
}

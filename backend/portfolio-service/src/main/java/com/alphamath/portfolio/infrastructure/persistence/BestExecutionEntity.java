package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_best_execution")
@Data
public class BestExecutionEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  private String proposalId;

  @Column(nullable = false)
  private String symbol;

  @Column(nullable = false)
  private String side;

  private Double requestedPrice;
  private Double executedPrice;
  private Double marketPrice;
  private Double slippageBps;

  private Instant createdAt;
}

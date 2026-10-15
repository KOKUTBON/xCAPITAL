package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_execution_fill")
@Data
public class ExecutionFillEntity {
  @Id
  private String id;

  @Column(name = "intent_id", nullable = false)
  private String intentId;

  @Column(name = "user_id", nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(name = "proposal_id", nullable = false)
  private String proposalId;

  @Column(nullable = false)
  private String symbol;

  @Column(nullable = false)
  private String side;

  @Column(nullable = false)
  private double quantity;

  @Column(nullable = false)
  private double price;

  @Column(nullable = false)
  private double fee;

  @Column(nullable = false)
  private String status;

  private Instant filledAt;
  private Instant createdAt;

  private String note;
}

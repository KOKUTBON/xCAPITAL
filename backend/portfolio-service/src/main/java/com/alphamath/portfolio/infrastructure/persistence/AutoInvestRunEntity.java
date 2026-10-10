package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_auto_invest_run")
@Data
public class AutoInvestRunEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String planId;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String trigger;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String idempotencyKey;

  private String proposalId;
  private String reason;

  @Lob
  private String metricsJson;

  private Instant createdAt;
  private Instant updatedAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_reward_enrollment")
@Data
public class RewardEnrollmentEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String offerId;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String status;

  private Instant qualifiedAt;
  private Instant paidAt;
  private Instant createdAt;
}

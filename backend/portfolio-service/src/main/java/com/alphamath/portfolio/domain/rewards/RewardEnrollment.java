package com.alphamath.portfolio.domain.rewards;

import lombok.Data;

import java.time.Instant;

@Data
public class RewardEnrollment {
  private String id;
  private String offerId;
  private String userId;
  private RewardEnrollmentStatus status;
  private Instant qualifiedAt;
  private Instant paidAt;
  private Instant createdAt;
}

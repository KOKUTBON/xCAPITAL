package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_notification_delivery")
@Data
public class NotificationDeliveryEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String notificationId;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String channel;

  private String destinationId;

  @Column(nullable = false)
  private String status;

  private String provider;

  @Column(nullable = false)
  private int attemptCount;

  private String lastError;
  private Instant lastAttemptAt;
  private Instant createdAt;
}

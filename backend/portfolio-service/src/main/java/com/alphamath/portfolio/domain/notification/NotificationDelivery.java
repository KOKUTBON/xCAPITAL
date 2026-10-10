package com.alphamath.portfolio.domain.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class NotificationDelivery {
  private String id;
  @JsonIgnore
  private String userId;
  private String notificationId;
  private NotificationChannel channel;
  private String destinationId;
  private NotificationDeliveryStatus status;
  private String provider;
  private int attemptCount;
  private String lastError;
  private Instant lastAttemptAt;
  private Instant createdAt;
}

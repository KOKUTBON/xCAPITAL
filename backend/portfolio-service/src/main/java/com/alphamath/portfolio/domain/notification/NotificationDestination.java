package com.alphamath.portfolio.domain.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class NotificationDestination {
  private String id;
  @JsonIgnore
  private String userId;
  private NotificationChannel channel;
  private String destination;
  private String label;
  private NotificationDestinationStatus status;
  private Instant createdAt;
  private Instant verifiedAt;
  private Instant updatedAt;
}

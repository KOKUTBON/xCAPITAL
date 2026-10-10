package com.alphamath.portfolio.domain.notification;

import lombok.Data;

@Data
public class NotificationDestinationRequest {
  private NotificationChannel channel;
  private String destination;
  private String label;
}

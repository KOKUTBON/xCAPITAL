package com.alphamath.portfolio.domain.notification;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class Notification {
  private String id;
  private String userId;
  private NotificationType type;
  private NotificationStatus status;
  private String title;
  private String body;
  private String entityType;
  private String entityId;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant readAt;
}

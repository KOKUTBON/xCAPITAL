package com.alphamath.portfolio.domain.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class NotificationPreference {
  private String id;
  @JsonIgnore
  private String userId;
  private NotificationChannel channel;
  private boolean enabled;
  private List<NotificationType> types = new ArrayList<>();
  private Integer quietStartHour;
  private Integer quietEndHour;
  private String timezone;
  private Instant createdAt;
  private Instant updatedAt;
}

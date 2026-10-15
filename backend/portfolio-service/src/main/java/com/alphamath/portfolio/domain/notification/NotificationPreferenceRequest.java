package com.alphamath.portfolio.domain.notification;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NotificationPreferenceRequest {
  private NotificationChannel channel;
  private Boolean enabled;
  private List<NotificationType> types = new ArrayList<>();
  private Integer quietStartHour;
  private Integer quietEndHour;
  private String timezone;
}

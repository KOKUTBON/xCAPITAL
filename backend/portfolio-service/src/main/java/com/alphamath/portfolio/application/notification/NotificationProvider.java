package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationChannel;
import com.alphamath.portfolio.domain.notification.NotificationDestination;

public interface NotificationProvider {
  boolean supports(String providerId, NotificationChannel channel);

  NotificationProviderResult send(NotificationChannel channel,
                                  Notification notification,
                                  NotificationDestination destination,
                                  String providerId);
}

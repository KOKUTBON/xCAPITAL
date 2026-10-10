package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.NotificationDeliveryStatus;
import lombok.Data;

@Data
public class NotificationProviderResult {
  private final NotificationDeliveryStatus status;
  private final String provider;
  private final String error;

  public static NotificationProviderResult sent(String provider) {
    return new NotificationProviderResult(NotificationDeliveryStatus.SENT, provider, null);
  }

  public static NotificationProviderResult failed(String provider, String error) {
    return new NotificationProviderResult(NotificationDeliveryStatus.FAILED, provider, error);
  }

  public static NotificationProviderResult skipped(String provider, String error) {
    return new NotificationProviderResult(NotificationDeliveryStatus.SKIPPED, provider, error);
  }

  public static NotificationProviderResult bounced(String provider, String error) {
    return new NotificationProviderResult(NotificationDeliveryStatus.BOUNCED, provider, error);
  }
}

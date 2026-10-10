package com.alphamath.portfolio.infrastructure.notification;

import com.alphamath.portfolio.application.notification.NotificationProvider;
import com.alphamath.portfolio.application.notification.NotificationProviderResult;
import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationChannel;
import com.alphamath.portfolio.domain.notification.NotificationDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class StubNotificationProvider implements NotificationProvider {
  private static final Logger log = LoggerFactory.getLogger(StubNotificationProvider.class);

  @Override
  public boolean supports(String providerId, NotificationChannel channel) {
    if (providerId == null || providerId.isBlank()) {
      return true;
    }
    return providerId.toLowerCase(Locale.US).startsWith("stub");
  }

  @Override
  public NotificationProviderResult send(NotificationChannel channel,
                                         Notification notification,
                                         NotificationDestination destination,
                                         String providerId) {
    String target = destination == null ? "internal" : destination.getDestination();
    String normalized = target == null ? "" : target.toLowerCase(Locale.US);

    if (normalized.contains("bounce") || normalized.contains("invalid") || normalized.contains("blocked")) {
      log.warn("Stub provider simulating bounce for {}", target);
      return NotificationProviderResult.bounced(providerId, "simulated bounce");
    }
    if (normalized.contains("fail")) {
      log.warn("Stub provider simulating failure for {}", target);
      return NotificationProviderResult.failed(providerId, "simulated failure");
    }
    if (normalized.contains("skip")) {
      return NotificationProviderResult.skipped(providerId, "simulated skip");
    }

    log.info("Stub provider delivered {} notification {} to {}", channel, notification.getId(), target);
    return NotificationProviderResult.sent(providerId);
  }
}

package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationChannel;
import com.alphamath.portfolio.domain.notification.NotificationDelivery;
import com.alphamath.portfolio.domain.notification.NotificationDeliveryStatus;
import com.alphamath.portfolio.domain.notification.NotificationDestination;
import com.alphamath.portfolio.domain.notification.NotificationDestinationStatus;
import com.alphamath.portfolio.domain.notification.NotificationPreference;
import com.alphamath.portfolio.domain.notification.NotificationType;
import com.alphamath.portfolio.infrastructure.persistence.NotificationDeliveryEntity;
import com.alphamath.portfolio.infrastructure.persistence.NotificationDeliveryRepository;
import com.alphamath.portfolio.infrastructure.persistence.NotificationDestinationEntity;
import com.alphamath.portfolio.infrastructure.persistence.NotificationDestinationRepository;
import com.alphamath.portfolio.infrastructure.persistence.NotificationEntity;
import com.alphamath.portfolio.infrastructure.persistence.NotificationRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class NotificationDeliveryService {
  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

  private final NotificationDeliveryRepository deliveries;
  private final NotificationRepository notifications;
  private final NotificationDestinationRepository destinationRepo;
  private final NotificationPreferenceService preferences;
  private final NotificationDestinationService destinations;
  private final NotificationDeliveryProperties properties;
  private final List<NotificationProvider> providers;
  private final TenantContext tenantContext;

  public NotificationDeliveryService(NotificationDeliveryRepository deliveries,
                                     NotificationRepository notifications,
                                     NotificationDestinationRepository destinationRepo,
                                     NotificationPreferenceService preferences,
                                     NotificationDestinationService destinations,
                                     NotificationDeliveryProperties properties,
                                     List<NotificationProvider> providers,
                                     TenantContext tenantContext) {
    this.deliveries = deliveries;
    this.notifications = notifications;
    this.destinationRepo = destinationRepo;
    this.preferences = preferences;
    this.destinations = destinations;
    this.properties = properties;
    this.providers = providers == null ? List.of() : providers;
    this.tenantContext = tenantContext;
  }

  public void dispatch(Notification notification) {
    if (notification == null) {
      return;
    }
    String userId = notification.getUserId();
    Instant now = Instant.now();

    for (NotificationChannel channel : NotificationChannel.values()) {
      NotificationPreference preference = preferences.find(userId, channel);
      if (preference == null && channel != NotificationChannel.IN_APP) {
        continue;
      }
      if (preference != null) {
        if (!preference.isEnabled()) {
          recordSkipped(notification, channel, null, "channel disabled", now);
          continue;
        }
        if (!allowsType(preference, notification.getType())) {
          recordSkipped(notification, channel, null, "type filtered", now);
          continue;
        }
      }

      if (channel == NotificationChannel.IN_APP) {
        recordSent(notification, channel, null, "internal", now);
        continue;
      }

      NotificationDeliveryProperties.ChannelSettings settings = properties.settingsFor(channel);
      if (!settings.isEnabled()) {
        recordSkipped(notification, channel, null, "channel disabled", now);
        continue;
      }

      List<NotificationDestination> channelDestinations = destinations.listVerified(userId, channel);
      if (channelDestinations.isEmpty()) {
        recordSkipped(notification, channel, null, "no verified destination", now);
        continue;
      }

      for (NotificationDestination destination : channelDestinations) {
        queueDelivery(notification, channel, destination, settings.getProvider(), now);
      }
    }
  }

  public List<NotificationDelivery> list(String userId, String status, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    var page = PageRequest.of(0, size);
    List<NotificationDeliveryEntity> rows;
    String orgId = tenantContext.getOrgId();
    if (status != null && !status.isBlank()) {
      rows = orgId == null
          ? deliveries.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.trim().toUpperCase(Locale.US), page)
          : deliveries.findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(userId, orgId, status.trim().toUpperCase(Locale.US), page);
    } else {
      rows = orgId == null
          ? deliveries.findByUserIdOrderByCreatedAtDesc(userId, page)
          : deliveries.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, page);
    }
    return rows.stream().map(this::toDto).toList();
  }

  @Scheduled(fixedDelayString = "${alphamath.notifications.delivery.pollDelayMs:10000}")
  public void processQueue() {
    if (!properties.isEnabled()) {
      return;
    }
    int batchSize = Math.max(1, properties.getBatchSize());
    var page = PageRequest.of(0, batchSize);
    List<NotificationDeliveryEntity> queue = deliveries.findByStatusInOrderByCreatedAtAsc(
        List.of(NotificationDeliveryStatus.PENDING.name(), NotificationDeliveryStatus.FAILED.name()), page);
    if (queue.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    for (NotificationDeliveryEntity entity : queue) {
      if (!eligibleForAttempt(entity, now)) {
        continue;
      }
      attemptDelivery(entity, now);
    }
  }

  private void attemptDelivery(NotificationDeliveryEntity entity, Instant now) {
    NotificationChannel channel = NotificationChannel.valueOf(entity.getChannel());
    if (channel == NotificationChannel.IN_APP) {
      markSent(entity, now, "internal");
      return;
    }
    NotificationDeliveryProperties.ChannelSettings settings = properties.settingsFor(channel);
    if (!settings.isEnabled()) {
      markSkipped(entity, now, "channel disabled");
      return;
    }

    NotificationEntity notificationEntity = notifications.findById(entity.getNotificationId()).orElse(null);
    if (notificationEntity == null) {
      markFailed(entity, now, settings.getProvider(), "notification not found");
      return;
    }

    NotificationDestination destination = null;
    NotificationDestinationEntity destinationEntity = null;
    if (channel != NotificationChannel.IN_APP) {
      if (entity.getDestinationId() == null) {
        markFailed(entity, now, settings.getProvider(), "destination missing");
        return;
      }
      destinationEntity = destinationRepo.findById(entity.getDestinationId()).orElse(null);
      if (destinationEntity == null) {
        markFailed(entity, now, settings.getProvider(), "destination not found");
        return;
      }
      if (!NotificationDestinationStatus.VERIFIED.name().equals(destinationEntity.getStatus())) {
        markSkipped(entity, now, "destination not verified");
        return;
      }
      destination = toDestinationDto(destinationEntity);
    }

    Notification notification = NotificationMapper.toDto(notificationEntity);
    NotificationPreference preference = preferences.find(notification.getUserId(), channel);
    if (preference != null) {
      if (!preference.isEnabled()) {
        markSkipped(entity, now, "channel disabled");
        return;
      }
      if (!allowsType(preference, notification.getType())) {
        markSkipped(entity, now, "type filtered");
        return;
      }
      if (isQuietHours(preference, now)) {
        defer(entity, now, "quiet hours");
        return;
      }
    }

    DeliveryAttempt attempt = deliver(channel, notification, destination, now, settings.getProvider());
    if (attempt.status == NotificationDeliveryStatus.SENT) {
      markSent(entity, now, attempt.provider);
    } else if (attempt.status == NotificationDeliveryStatus.FAILED) {
      markFailed(entity, now, attempt.provider, attempt.error);
    } else if (attempt.status == NotificationDeliveryStatus.BOUNCED) {
      markBounced(entity, now, attempt.provider, attempt.error);
      if (destinationEntity != null) {
        disableDestination(destinationEntity, now, attempt.error);
      }
    } else {
      markSkipped(entity, now, attempt.error);
    }
  }

  private boolean eligibleForAttempt(NotificationDeliveryEntity entity, Instant now) {
    int attempts = entity.getAttemptCount();
    if (attempts >= properties.getMaxAttempts()) {
      if (!NotificationDeliveryStatus.FAILED.name().equals(entity.getStatus())) {
        entity.setStatus(NotificationDeliveryStatus.FAILED.name());
      }
      if (entity.getLastError() == null || entity.getLastError().isBlank()) {
        entity.setLastError("max attempts reached");
      }
      deliveries.save(entity);
      return false;
    }
    Instant lastAttempt = entity.getLastAttemptAt();
    if (lastAttempt == null) {
      return true;
    }
    long delaySeconds = computeBackoffSeconds(attempts);
    return lastAttempt.plusSeconds(delaySeconds).isBefore(now);
  }

  private long computeBackoffSeconds(int attemptCount) {
    long base = Math.max(1, properties.getBaseDelaySeconds());
    int exp = Math.max(0, attemptCount - 1);
    long delay = base * (1L << Math.min(exp, 10));
    return Math.min(delay, Math.max(base, properties.getMaxDelaySeconds()));
  }

  private DeliveryAttempt deliver(NotificationChannel channel, Notification notification,
                                  NotificationDestination destination, Instant now, String providerId) {
    NotificationProvider provider = providerFor(providerId, channel);
    NotificationProviderResult result = provider.send(channel, notification, destination, providerId);
    NotificationDeliveryStatus status = result == null || result.getStatus() == null
        ? NotificationDeliveryStatus.FAILED
        : result.getStatus();
    String providerName = result == null || result.getProvider() == null ? providerId : result.getProvider();
    String error = result == null ? "provider error" : result.getError();
    try {
      String target = destination == null ? "internal" : destination.getDestination();
      log.info("Delivering {} notification {} via {} to {} (status={})",
          channel, notification.getId(), providerName, target, status.name());
    } catch (Exception e) {
      return new DeliveryAttempt(NotificationDeliveryStatus.FAILED, providerName, e.getMessage());
    }
    return new DeliveryAttempt(status, providerName, error);
  }

  private boolean allowsType(NotificationPreference preference, NotificationType type) {
    if (preference.getTypes() == null || preference.getTypes().isEmpty()) {
      return true;
    }
    return preference.getTypes().contains(type);
  }

  private boolean isQuietHours(NotificationPreference preference, Instant now) {
    Integer start = preference.getQuietStartHour();
    Integer end = preference.getQuietEndHour();
    if (start == null || end == null) {
      return false;
    }
    if (start.equals(end)) {
      return false;
    }
    ZoneId zone = ZoneId.of("UTC");
    if (preference.getTimezone() != null && !preference.getTimezone().isBlank()) {
      try {
        zone = ZoneId.of(preference.getTimezone());
      } catch (Exception ignored) {
      }
    }
    ZonedDateTime local = ZonedDateTime.ofInstant(now, zone);
    int hour = local.getHour();
    if (start < end) {
      return hour >= start && hour < end;
    }
    return hour >= start || hour < end;
  }

  private void recordSent(Notification notification, NotificationChannel channel,
                          NotificationDestination destination, String provider, Instant now) {
    NotificationDeliveryEntity entity = baseEntity(notification, channel, destination, now);
    entity.setStatus(NotificationDeliveryStatus.SENT.name());
    entity.setProvider(provider);
    entity.setAttemptCount(1);
    entity.setLastAttemptAt(now);
    deliveries.save(entity);
  }

  private void recordFailed(Notification notification, NotificationChannel channel,
                            NotificationDestination destination, String provider, String error, Instant now) {
    NotificationDeliveryEntity entity = baseEntity(notification, channel, destination, now);
    entity.setStatus(NotificationDeliveryStatus.FAILED.name());
    entity.setProvider(provider);
    entity.setAttemptCount(1);
    entity.setLastAttemptAt(now);
    entity.setLastError(error == null ? "delivery failed" : error);
    deliveries.save(entity);
  }

  private void recordSkipped(Notification notification, NotificationChannel channel,
                             NotificationDestination destination, String reason, Instant now) {
    NotificationDeliveryEntity entity = baseEntity(notification, channel, destination, now);
    entity.setStatus(NotificationDeliveryStatus.SKIPPED.name());
    entity.setLastError(reason);
    deliveries.save(entity);
  }

  private void queueDelivery(Notification notification, NotificationChannel channel,
                             NotificationDestination destination, String provider, Instant now) {
    NotificationDeliveryEntity entity = baseEntity(notification, channel, destination, now);
    entity.setProvider(provider);
    entity.setStatus(NotificationDeliveryStatus.PENDING.name());
    deliveries.save(entity);
  }

  private NotificationDeliveryEntity baseEntity(Notification notification, NotificationChannel channel,
                                                NotificationDestination destination, Instant now) {
    NotificationDeliveryEntity entity = new NotificationDeliveryEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setNotificationId(notification.getId());
    entity.setUserId(notification.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setChannel(channel.name());
    entity.setDestinationId(destination == null ? null : destination.getId());
    entity.setStatus(NotificationDeliveryStatus.PENDING.name());
    entity.setAttemptCount(0);
    entity.setCreatedAt(now);
    return entity;
  }

  private NotificationDelivery toDto(NotificationDeliveryEntity entity) {
    NotificationDelivery out = new NotificationDelivery();
    out.setId(entity.getId());
    out.setNotificationId(entity.getNotificationId());
    out.setUserId(entity.getUserId());
    out.setChannel(NotificationChannel.valueOf(entity.getChannel()));
    out.setDestinationId(entity.getDestinationId());
    out.setStatus(NotificationDeliveryStatus.valueOf(entity.getStatus()));
    out.setProvider(entity.getProvider());
    out.setAttemptCount(entity.getAttemptCount());
    out.setLastError(entity.getLastError());
    out.setLastAttemptAt(entity.getLastAttemptAt());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private NotificationDestination toDestinationDto(NotificationDestinationEntity entity) {
    NotificationDestination out = new NotificationDestination();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setChannel(NotificationChannel.valueOf(entity.getChannel()));
    out.setDestination(entity.getDestination());
    out.setLabel(entity.getLabel());
    out.setStatus(NotificationDestinationStatus.valueOf(entity.getStatus()));
    out.setCreatedAt(entity.getCreatedAt());
    out.setVerifiedAt(entity.getVerifiedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private void markSent(NotificationDeliveryEntity entity, Instant now, String provider) {
    entity.setStatus(NotificationDeliveryStatus.SENT.name());
    entity.setProvider(provider);
    entity.setAttemptCount(entity.getAttemptCount() + 1);
    entity.setLastAttemptAt(now);
    entity.setLastError(null);
    deliveries.save(entity);
  }

  private void markFailed(NotificationDeliveryEntity entity, Instant now, String provider, String error) {
    entity.setStatus(NotificationDeliveryStatus.FAILED.name());
    entity.setProvider(provider);
    entity.setAttemptCount(entity.getAttemptCount() + 1);
    entity.setLastAttemptAt(now);
    entity.setLastError(error == null ? "delivery failed" : error);
    deliveries.save(entity);
  }

  private void markSkipped(NotificationDeliveryEntity entity, Instant now, String reason) {
    entity.setStatus(NotificationDeliveryStatus.SKIPPED.name());
    entity.setAttemptCount(entity.getAttemptCount() + 1);
    entity.setLastAttemptAt(now);
    entity.setLastError(reason == null ? "skipped" : reason);
    deliveries.save(entity);
  }

  private void markBounced(NotificationDeliveryEntity entity, Instant now, String provider, String reason) {
    entity.setStatus(NotificationDeliveryStatus.BOUNCED.name());
    entity.setProvider(provider);
    entity.setAttemptCount(entity.getAttemptCount() + 1);
    entity.setLastAttemptAt(now);
    entity.setLastError(reason == null ? "bounced" : reason);
    deliveries.save(entity);
  }

  private void disableDestination(NotificationDestinationEntity entity, Instant now, String reason) {
    if (NotificationDestinationStatus.DISABLED.name().equals(entity.getStatus())) {
      return;
    }
    entity.setStatus(NotificationDestinationStatus.DISABLED.name());
    entity.setUpdatedAt(now);
    destinationRepo.save(entity);
    log.warn("Disabled destination {} due to {}", entity.getId(), reason);
  }

  private void defer(NotificationDeliveryEntity entity, Instant now, String reason) {
    entity.setStatus(NotificationDeliveryStatus.PENDING.name());
    entity.setLastAttemptAt(now);
    entity.setLastError(reason == null ? "deferred" : reason);
    deliveries.save(entity);
  }

  private static class DeliveryAttempt {
    final NotificationDeliveryStatus status;
    final String provider;
    final String error;

    DeliveryAttempt(NotificationDeliveryStatus status, String provider, String error) {
      this.status = status;
      this.provider = provider;
      this.error = error;
    }
  }

  private NotificationProvider providerFor(String providerId, NotificationChannel channel) {
    for (NotificationProvider provider : providers) {
      if (provider.supports(providerId, channel)) {
        return provider;
      }
    }
    return new NoopNotificationProvider();
  }

  private static class NoopNotificationProvider implements NotificationProvider {
    @Override
    public boolean supports(String providerId, NotificationChannel channel) {
      return true;
    }

    @Override
    public NotificationProviderResult send(NotificationChannel channel,
                                           Notification notification,
                                           NotificationDestination destination,
                                           String providerId) {
      return NotificationProviderResult.skipped(providerId, "provider not configured");
    }
  }
}

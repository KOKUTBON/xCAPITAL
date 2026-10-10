package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.NotificationChannel;
import com.alphamath.portfolio.domain.notification.NotificationDestination;
import com.alphamath.portfolio.domain.notification.NotificationDestinationRequest;
import com.alphamath.portfolio.domain.notification.NotificationDestinationStatus;
import com.alphamath.portfolio.infrastructure.persistence.NotificationDestinationEntity;
import com.alphamath.portfolio.infrastructure.persistence.NotificationDestinationRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class NotificationDestinationService {
  private final NotificationDestinationRepository destinations;
  private final TenantContext tenantContext;

  public NotificationDestinationService(NotificationDestinationRepository destinations, TenantContext tenantContext) {
    this.destinations = destinations;
    this.tenantContext = tenantContext;
  }

  public List<NotificationDestination> list(String userId, String channel) {
    List<NotificationDestinationEntity> rows;
    String orgId = tenantContext.getOrgId();
    if (channel != null && !channel.isBlank()) {
      NotificationChannel parsed = parseChannel(channel);
      rows = orgId == null
          ? destinations.findByUserIdAndChannelOrderByCreatedAtDesc(userId, parsed.name())
          : destinations.findByUserIdAndOrgIdAndChannelOrderByCreatedAtDesc(userId, orgId, parsed.name());
    } else {
      rows = orgId == null
          ? destinations.findByUserIdOrderByCreatedAtDesc(userId)
          : destinations.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    }
    return rows.stream().map(this::toDto).toList();
  }

  public List<NotificationDestination> listVerified(String userId, NotificationChannel channel) {
    if (channel == null) {
      return List.of();
    }
    String orgId = tenantContext.getOrgId();
    List<NotificationDestinationEntity> rows = orgId == null
        ? destinations.findByUserIdAndChannelAndStatus(userId, channel.name(), NotificationDestinationStatus.VERIFIED.name())
        : destinations.findByUserIdAndOrgIdAndChannelAndStatus(userId, orgId, channel.name(), NotificationDestinationStatus.VERIFIED.name());
    return rows.stream().map(this::toDto).toList();
  }

  public NotificationDestination create(String userId, NotificationDestinationRequest req) {
    if (req == null || req.getChannel() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel is required");
    }
    if (req.getChannel() == NotificationChannel.IN_APP) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IN_APP channel does not require destinations");
    }
    String destination = normalizeDestination(req.getDestination());
    if (destination.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "destination is required");
    }
    validateDestination(req.getChannel(), destination);

    NotificationDestinationEntity entity = new NotificationDestinationEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setChannel(req.getChannel().name());
    entity.setDestination(destination);
    entity.setLabel(req.getLabel() == null ? null : req.getLabel().trim());
    entity.setStatus(NotificationDestinationStatus.PENDING.name());
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    destinations.save(entity);
    return toDto(entity);
  }

  public NotificationDestination verify(String userId, String id) {
    NotificationDestinationEntity entity = loadOwned(userId, id);
    if (!NotificationDestinationStatus.VERIFIED.name().equals(entity.getStatus())) {
      entity.setStatus(NotificationDestinationStatus.VERIFIED.name());
      entity.setVerifiedAt(Instant.now());
      entity.setUpdatedAt(Instant.now());
      destinations.save(entity);
    }
    return toDto(entity);
  }

  public NotificationDestination disable(String userId, String id) {
    NotificationDestinationEntity entity = loadOwned(userId, id);
    entity.setStatus(NotificationDestinationStatus.DISABLED.name());
    entity.setUpdatedAt(Instant.now());
    destinations.save(entity);
    return toDto(entity);
  }

  private NotificationDestinationEntity loadOwned(String userId, String id) {
    NotificationDestinationEntity entity = destinations.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found");
    }
    return entity;
  }

  private NotificationDestination toDto(NotificationDestinationEntity entity) {
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

  private NotificationChannel parseChannel(String raw) {
    try {
      return NotificationChannel.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid channel");
    }
  }

  private String normalizeDestination(String destination) {
    return destination == null ? "" : destination.trim();
  }

  private void validateDestination(NotificationChannel channel, String destination) {
    switch (channel) {
      case EMAIL -> {
        if (!destination.contains("@")) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email destination");
        }
      }
      case SMS -> {
        String digits = destination.replaceAll("[^0-9+]", "");
        if (digits.length() < 8) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid phone destination");
        }
      }
      case WEBHOOK -> {
        String lower = destination.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "webhook must start with http(s)");
        }
      }
      case PUSH -> {
        if (destination.length() < 12) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid push token");
        }
      }
      default -> {
      }
    }
  }
}

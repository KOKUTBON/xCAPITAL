package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.NotificationChannel;
import com.alphamath.portfolio.domain.notification.NotificationPreference;
import com.alphamath.portfolio.domain.notification.NotificationPreferenceRequest;
import com.alphamath.portfolio.domain.notification.NotificationType;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.NotificationPreferenceEntity;
import com.alphamath.portfolio.infrastructure.persistence.NotificationPreferenceRepository;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationPreferenceService {
  private final NotificationPreferenceRepository preferences;
  private final TenantContext tenantContext;

  public NotificationPreferenceService(NotificationPreferenceRepository preferences, TenantContext tenantContext) {
    this.preferences = preferences;
    this.tenantContext = tenantContext;
  }

  public List<NotificationPreference> list(String userId) {
    String orgId = tenantContext.getOrgId();
    List<NotificationPreferenceEntity> rows = orgId == null
        ? preferences.findByUserIdOrderByChannelAsc(userId)
        : preferences.findByUserIdAndOrgIdOrderByChannelAsc(userId, orgId);
    return rows.stream().map(this::toDto).toList();
  }

  public NotificationPreference upsert(String userId, NotificationPreferenceRequest req) {
    if (req == null || req.getChannel() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel is required");
    }
    NotificationChannel channel = req.getChannel();
    validateQuietHours(req.getQuietStartHour(), req.getQuietEndHour());

    String orgId = tenantContext.getOrgId();
    NotificationPreferenceEntity entity = orgId == null
        ? preferences.findByUserIdAndChannel(userId, channel.name())
        : preferences.findByUserIdAndOrgIdAndChannel(userId, orgId, channel.name());
    if (entity == null) {
      entity = new NotificationPreferenceEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setOrgId(tenantContext.getOrgId());
      entity.setChannel(channel.name());
      entity.setCreatedAt(Instant.now());
      entity.setEnabled(req.getEnabled() == null ? true : req.getEnabled());
    } else if (req.getEnabled() != null) {
      entity.setEnabled(req.getEnabled());
    }

    List<String> types = normalizeTypes(req.getTypes());
    entity.setTypesJson(JsonUtils.toJson(types));
    entity.setQuietStartHour(req.getQuietStartHour());
    entity.setQuietEndHour(req.getQuietEndHour());
    entity.setTimezone(normalizeTimezone(req.getTimezone()));
    entity.setUpdatedAt(Instant.now());

    preferences.save(entity);
    return toDto(entity);
  }

  public NotificationPreference find(String userId, NotificationChannel channel) {
    if (channel == null) {
      return null;
    }
    String orgId = tenantContext.getOrgId();
    NotificationPreferenceEntity entity = orgId == null
        ? preferences.findByUserIdAndChannel(userId, channel.name())
        : preferences.findByUserIdAndOrgIdAndChannel(userId, orgId, channel.name());
    return entity == null ? null : toDto(entity);
  }

  private NotificationPreference toDto(NotificationPreferenceEntity entity) {
    NotificationPreference out = new NotificationPreference();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setChannel(NotificationChannel.valueOf(entity.getChannel()));
    out.setEnabled(entity.isEnabled());
    out.setTypes(parseTypes(entity.getTypesJson()));
    out.setQuietStartHour(entity.getQuietStartHour());
    out.setQuietEndHour(entity.getQuietEndHour());
    out.setTimezone(entity.getTimezone());
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private List<NotificationType> parseTypes(String json) {
    if (json == null || json.isBlank()) {
      return new ArrayList<>();
    }
    try {
      List<String> values = JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
      Map<String, NotificationType> uniq = new LinkedHashMap<>();
      for (String value : values) {
        if (value == null || value.isBlank()) continue;
        try {
          NotificationType type = NotificationType.valueOf(value.trim().toUpperCase(Locale.US));
          uniq.put(type.name(), type);
        } catch (IllegalArgumentException ignored) {
        }
      }
      return new ArrayList<>(uniq.values());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private List<String> normalizeTypes(List<NotificationType> types) {
    if (types == null || types.isEmpty()) {
      return List.of();
    }
    Map<String, Boolean> uniq = new LinkedHashMap<>();
    for (NotificationType type : types) {
      if (type == null) continue;
      uniq.put(type.name(), true);
    }
    return new ArrayList<>(uniq.keySet());
  }

  private String normalizeTimezone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return null;
    }
    return timezone.trim();
  }

  private void validateQuietHours(Integer start, Integer end) {
    if (start != null && (start < 0 || start > 23)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quietStartHour must be 0-23");
    }
    if (end != null && (end < 0 || end > 23)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quietEndHour must be 0-23");
    }
  }
}

package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationStatus;
import com.alphamath.portfolio.domain.notification.NotificationType;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.NotificationEntity;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NotificationMapper {
  private NotificationMapper() {}

  public static Notification toDto(NotificationEntity entity) {
    Notification out = new Notification();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setType(NotificationType.valueOf(entity.getType()));
    out.setStatus(NotificationStatus.valueOf(entity.getStatus()));
    out.setTitle(entity.getTitle());
    out.setBody(entity.getBody());
    out.setEntityType(entity.getEntityType());
    out.setEntityId(entity.getEntityId());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    out.setReadAt(entity.getReadAt());
    return out;
  }

  private static Map<String, Object> parseMetadata(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }
}

package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationStatus;
import com.alphamath.portfolio.domain.notification.NotificationType;
import com.alphamath.portfolio.infrastructure.persistence.NotificationEntity;
import com.alphamath.portfolio.infrastructure.persistence.NotificationRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
  private final NotificationRepository notifications;
  private final NotificationDeliveryService deliveryService;
  private final TenantContext tenantContext;

  public NotificationService(NotificationRepository notifications,
                             NotificationDeliveryService deliveryService,
                             TenantContext tenantContext) {
    this.notifications = notifications;
    this.deliveryService = deliveryService;
    this.tenantContext = tenantContext;
  }

  public Notification create(String userId, NotificationType type, String title, String body,
                             String entityType, String entityId, Map<String, Object> metadata) {
    NotificationEntity entity = new NotificationEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setType(type.name());
    entity.setStatus(NotificationStatus.UNREAD.name());
    entity.setTitle(title);
    entity.setBody(body);
    entity.setEntityType(entityType);
    entity.setEntityId(entityId);
    entity.setMetadataJson(com.alphamath.portfolio.infrastructure.persistence.JsonUtils.toJson(metadata == null ? Map.of() : metadata));
    entity.setCreatedAt(Instant.now());
    notifications.save(entity);
    Notification out = NotificationMapper.toDto(entity);
    deliveryService.dispatch(out);
    return out;
  }

  public List<Notification> list(String userId, String status, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    var page = PageRequest.of(0, size);
    List<NotificationEntity> rows;
    String orgId = tenantContext.getOrgId();
    if (status != null && !status.isBlank()) {
      rows = orgId == null
          ? notifications.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.trim().toUpperCase(), page)
          : notifications.findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(userId, orgId, status.trim().toUpperCase(), page);
    } else {
      rows = orgId == null
          ? notifications.findByUserIdOrderByCreatedAtDesc(userId, page)
          : notifications.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, page);
    }
    return rows.stream().map(NotificationMapper::toDto).toList();
  }

  public Notification markRead(String userId, String id) {
    NotificationEntity entity = notifications.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
    }
    if (!NotificationStatus.READ.name().equals(entity.getStatus())) {
      entity.setStatus(NotificationStatus.READ.name());
      entity.setReadAt(Instant.now());
      notifications.save(entity);
    }
    return NotificationMapper.toDto(entity);
  }
}

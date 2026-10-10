package com.alphamath.portfolio.application.audit;

import com.alphamath.portfolio.domain.audit.AuditEvent;
import com.alphamath.portfolio.infrastructure.persistence.AuditEventEntity;
import com.alphamath.portfolio.infrastructure.persistence.AuditEventRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
  private final AuditEventRepository events;
  private final TenantContext tenantContext;

  public AuditService(AuditEventRepository events, TenantContext tenantContext) {
    this.events = events;
    this.tenantContext = tenantContext;
  }

  public void record(String userId, String actor, String eventType, String entityType, String entityId,
                     Map<String, Object> metadata) {
    AuditEventEntity entity = new AuditEventEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setActor(actor == null || actor.isBlank() ? "system" : actor);
    entity.setEventType(eventType);
    entity.setEntityType(entityType);
    entity.setEntityId(entityId);
    entity.setCreatedAt(Instant.now());
    String metadataJson = JsonUtils.toJson(metadata == null ? Map.of() : metadata);
    entity.setMetadataJson(metadataJson);

    String orgId = tenantContext.getOrgId();
    AuditEventEntity last = orgId == null
        ? events.findTopByUserIdOrderByCreatedAtDesc(userId)
        : events.findTopByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    String prevHash = last == null ? null : last.getEventHash();
    entity.setPrevHash(prevHash);
    entity.setEventHash(hashEvent(entity, metadataJson, prevHash));
    events.save(entity);
  }

  public List<AuditEvent> list(String userId, String eventType, String entityId, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    var page = PageRequest.of(0, size);

    List<AuditEventEntity> rows;
    String orgId = tenantContext.getOrgId();
    if (eventType != null && !eventType.isBlank()) {
      rows = orgId == null
          ? events.findByUserIdAndEventTypeOrderByCreatedAtDesc(userId, eventType.trim(), page)
          : events.findByUserIdAndOrgIdAndEventTypeOrderByCreatedAtDesc(userId, orgId, eventType.trim(), page);
    } else if (entityId != null && !entityId.isBlank()) {
      rows = orgId == null
          ? events.findByUserIdAndEntityIdOrderByCreatedAtDesc(userId, entityId.trim(), page)
          : events.findByUserIdAndOrgIdAndEntityIdOrderByCreatedAtDesc(userId, orgId, entityId.trim(), page);
    } else {
      rows = orgId == null
          ? events.findByUserIdOrderByCreatedAtDesc(userId, page)
          : events.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, page);
    }
    return rows.stream().map(this::toDto).toList();
  }

  public String exportCsv(String userId, String eventType, String entityId, int limit) {
    List<AuditEvent> rows = list(userId, eventType, entityId, limit);
    StringBuilder sb = new StringBuilder();
    sb.append("id,orgId,actor,eventType,entityType,entityId,createdAt,prevHash,eventHash,metadata").append("\n");
    for (AuditEvent event : rows) {
      sb.append(escape(event.getId())).append(',')
        .append(escape(event.getOrgId())).append(',')
        .append(escape(event.getActor())).append(',')
        .append(escape(event.getEventType())).append(',')
        .append(escape(event.getEntityType())).append(',')
        .append(escape(event.getEntityId())).append(',')
        .append(escape(event.getCreatedAt() == null ? null : event.getCreatedAt().toString())).append(',')
        .append(escape(event.getPrevHash())).append(',')
        .append(escape(event.getEventHash())).append(',')
        .append(escape(JsonUtils.toJson(event.getMetadata())))
        .append("\n");
    }
    return sb.toString();
  }

  private String escape(String value) {
    if (value == null) return "";
    String escaped = value.replace("\"", "\"\"");
    return "\"" + escaped + "\"";
  }

  private AuditEvent toDto(AuditEventEntity entity) {
    AuditEvent out = new AuditEvent();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setOrgId(entity.getOrgId());
    out.setActor(entity.getActor());
    out.setEventType(entity.getEventType());
    out.setEntityType(entity.getEntityType());
    out.setEntityId(entity.getEntityId());
    out.setCreatedAt(entity.getCreatedAt());
    out.setPrevHash(entity.getPrevHash());
    out.setEventHash(entity.getEventHash());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    return out;
  }

  private String hashEvent(AuditEventEntity entity, String metadataJson, String prevHash) {
    String payload = String.join("|",
        safe(prevHash),
        safe(entity.getUserId()),
        safe(entity.getOrgId()),
        safe(entity.getActor()),
        safe(entity.getEventType()),
        safe(entity.getEntityType()),
        safe(entity.getEntityId()),
        entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString(),
        safe(metadataJson)
    );
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      return null;
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private Map<String, Object> parseMetadata(String json) {
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

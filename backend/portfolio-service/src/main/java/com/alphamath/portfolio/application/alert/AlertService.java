package com.alphamath.portfolio.application.alert;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.notification.NotificationService;
import com.alphamath.portfolio.domain.alert.Alert;
import com.alphamath.portfolio.domain.alert.AlertComparison;
import com.alphamath.portfolio.domain.alert.AlertFrequency;
import com.alphamath.portfolio.domain.alert.AlertRequest;
import com.alphamath.portfolio.domain.alert.AlertStatus;
import com.alphamath.portfolio.domain.alert.AlertType;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.notification.NotificationType;
import com.alphamath.portfolio.infrastructure.persistence.AlertEntity;
import com.alphamath.portfolio.infrastructure.persistence.AlertRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.PageRequest;
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
public class AlertService {
  private final AlertRepository alerts;
  private final NotificationService notifications;
  private final AuditService audit;
  private final TenantContext tenantContext;

  public AlertService(AlertRepository alerts,
                      NotificationService notifications,
                      AuditService audit,
                      TenantContext tenantContext) {
    this.alerts = alerts;
    this.notifications = notifications;
    this.audit = audit;
    this.tenantContext = tenantContext;
  }

  public Alert create(String userId, AlertRequest req) {
    if (req == null || req.getAlertType() == null || req.getAlertType().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alert type required");
    }
    AlertEntity entity = new AlertEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setStatus(AlertStatus.ACTIVE);
    entity.setAlertType(parseType(req.getAlertType()));
    entity.setSymbol(req.getSymbol() == null ? null : req.getSymbol().trim().toUpperCase(Locale.US));
    entity.setInstrumentId(req.getInstrumentId());
    entity.setAssetClass(parseAssetClass(req.getAssetClass()));
    entity.setComparison(parseComparison(req.getComparison()));
    entity.setTargetValue(req.getTargetValue());
    entity.setFrequency(parseFrequency(req.getFrequency()));
    entity.setConditionJson(JsonUtils.toJson(req.getCondition() == null ? Map.of() : req.getCondition()));
    entity.setAiScore(req.getAiScore());
    entity.setAiSummary(req.getAiSummary());
    entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    alerts.save(entity);

    audit.record(userId, userId, "ALERT_CREATED", "portfolio_alert", entity.getId(),
        Map.of("type", entity.getAlertType().name(), "symbol", entity.getSymbol()));
    return toDto(entity);
  }

  public List<Alert> list(String userId, String status, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    PageRequest page = PageRequest.of(0, size);

    List<AlertEntity> rows;
    String orgId = tenantContext.getOrgId();
    if (status != null && !status.isBlank()) {
      AlertStatus parsed = parseStatus(status);
      rows = orgId == null
          ? alerts.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, parsed, page)
          : alerts.findByUserIdAndOrgIdAndStatusOrderByUpdatedAtDesc(userId, orgId, parsed, page);
    } else {
      rows = orgId == null
          ? alerts.findByUserIdOrderByUpdatedAtDesc(userId, page)
          : alerts.findByUserIdAndOrgIdOrderByUpdatedAtDesc(userId, orgId, page);
    }

    List<Alert> out = new ArrayList<>();
    for (AlertEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  public Alert updateStatus(String userId, String id, String status) {
    AlertEntity entity = alerts.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
    }
    entity.setStatus(parseStatus(status));
    entity.setUpdatedAt(Instant.now());
    alerts.save(entity);

    audit.record(userId, userId, "ALERT_STATUS", "portfolio_alert", entity.getId(),
        Map.of("status", entity.getStatus().name()));
    return toDto(entity);
  }

  public Alert trigger(String userId, String id, Map<String, Object> metadata) {
    AlertEntity entity = alerts.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
    }
    entity.setStatus(AlertStatus.TRIGGERED);
    entity.setLastTriggeredAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    alerts.save(entity);

    notifications.create(userId, NotificationType.ALERT_TRIGGERED,
        "Alert triggered", buildAlertBody(entity),
        "portfolio_alert", entity.getId(), metadata == null ? Map.of() : metadata);

    audit.record(userId, userId, "ALERT_TRIGGERED", "portfolio_alert", entity.getId(),
        Map.of("symbol", entity.getSymbol(), "type", entity.getAlertType().name()));
    return toDto(entity);
  }

  private String buildAlertBody(AlertEntity entity) {
    String symbol = entity.getSymbol() == null ? "" : entity.getSymbol();
    String type = entity.getAlertType() == null ? "ALERT" : entity.getAlertType().name();
    return symbol.isBlank() ? "Alert triggered: " + type : "Alert triggered: " + type + " for " + symbol;
  }

  private Alert toDto(AlertEntity entity) {
    Alert out = new Alert();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setStatus(entity.getStatus());
    out.setAlertType(entity.getAlertType());
    out.setSymbol(entity.getSymbol());
    out.setInstrumentId(entity.getInstrumentId());
    out.setAssetClass(entity.getAssetClass());
    out.setComparison(entity.getComparison());
    out.setTargetValue(entity.getTargetValue());
    out.setFrequency(entity.getFrequency());
    out.setCondition(parseMetadata(entity.getConditionJson()));
    out.setAiScore(entity.getAiScore());
    out.setAiSummary(entity.getAiSummary());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    out.setLastTriggeredAt(entity.getLastTriggeredAt());
    out.setLastCheckedAt(entity.getLastCheckedAt());
    return out;
  }

  private AlertStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) return AlertStatus.ACTIVE;
    return AlertStatus.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private AlertType parseType(String raw) {
    return AlertType.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private AlertComparison parseComparison(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return AlertComparison.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private AlertFrequency parseFrequency(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return AlertFrequency.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private AssetClass parseAssetClass(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return AssetClass.valueOf(raw.trim().toUpperCase(Locale.US));
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

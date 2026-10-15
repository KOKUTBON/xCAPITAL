package com.alphamath.portfolio.application.ai;

import com.alphamath.portfolio.domain.ai.ModelRegisterRequest;
import com.alphamath.portfolio.domain.ai.ModelRegistryEntry;
import com.alphamath.portfolio.domain.ai.ModelStatus;
import com.alphamath.portfolio.infrastructure.persistence.AiModelRegistryEntity;
import com.alphamath.portfolio.infrastructure.persistence.AiModelRegistryRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ModelRegistryService {
  private final AiModelRegistryRepository registry;

  public ModelRegistryService(AiModelRegistryRepository registry) {
    this.registry = registry;
  }

  public ModelRegistryEntry register(ModelRegisterRequest req) {
    AiModelRegistryEntity entity = new AiModelRegistryEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setModelName(req.getModelName().trim());
    entity.setVersion(req.getVersion().trim());
    entity.setStatus(req.getStatus() == null ? ModelStatus.DEPLOYED.name() : req.getStatus().name());
    entity.setTrainingStart(req.getTrainingStart());
    entity.setTrainingEnd(req.getTrainingEnd());
    entity.setMetricsJson(JsonUtils.toJson(req.getMetrics() == null ? Map.of() : req.getMetrics()));
    entity.setCreatedAt(Instant.now());
    entity.setDeployedAt(entity.getStatus().equals(ModelStatus.DEPLOYED.name()) ? entity.getCreatedAt() : null);
    registry.save(entity);
    return toDto(entity);
  }

  public ModelRegistryEntry get(String id) {
    AiModelRegistryEntity entity = registry.findById(id).orElse(null);
    if (entity == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model entry not found");
    }
    return toDto(entity);
  }

  public List<ModelRegistryEntry> list(String modelName, String status, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    var page = PageRequest.of(0, size);
    List<AiModelRegistryEntity> rows;
    if (modelName != null && !modelName.isBlank()) {
      rows = registry.findByModelNameOrderByCreatedAtDesc(modelName.trim(), page);
    } else if (status != null && !status.isBlank()) {
      rows = registry.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), page);
    } else {
      rows = registry.findAll(page).getContent();
    }
    return rows.stream().map(this::toDto).toList();
  }

  public ModelRegistryEntry updateStatus(String id, ModelStatus status) {
    AiModelRegistryEntity entity = registry.findById(id).orElse(null);
    if (entity == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model entry not found");
    }
    entity.setStatus(status.name());
    if (status == ModelStatus.DEPLOYED) {
      entity.setDeployedAt(Instant.now());
    }
    registry.save(entity);
    return toDto(entity);
  }

  private ModelRegistryEntry toDto(AiModelRegistryEntity entity) {
    ModelRegistryEntry out = new ModelRegistryEntry();
    out.setId(entity.getId());
    out.setModelName(entity.getModelName());
    out.setVersion(entity.getVersion());
    out.setStatus(ModelStatus.valueOf(entity.getStatus()));
    out.setTrainingStart(entity.getTrainingStart());
    out.setTrainingEnd(entity.getTrainingEnd());
    out.setMetrics(parseMetrics(entity.getMetricsJson()));
    out.setCreatedAt(entity.getCreatedAt());
    out.setDeployedAt(entity.getDeployedAt());
    return out;
  }

  private Map<String, Object> parseMetrics(String json) {
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

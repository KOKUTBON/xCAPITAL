package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "ai_model_registry")
@Data
public class AiModelRegistryEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String modelName;

  @Column(nullable = false)
  private String version;

  @Column(nullable = false)
  private String status;

  private Instant trainingStart;
  private Instant trainingEnd;

  @Lob
  @Column(nullable = false)
  private String metricsJson;

  private Instant createdAt;
  private Instant deployedAt;
}

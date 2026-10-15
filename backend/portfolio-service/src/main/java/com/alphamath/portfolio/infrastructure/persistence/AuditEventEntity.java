package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_audit_event")
@Data
public class AuditEventEntity {
  @Id
  private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String actor;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  private String entityType;
  private String entityId;

  private Instant createdAt;

  @Column(name = "prev_hash")
  private String prevHash;

  @Column(name = "event_hash")
  private String eventHash;

  @Column(name = "metadata_json", nullable = false)
  private String metadataJson;
}

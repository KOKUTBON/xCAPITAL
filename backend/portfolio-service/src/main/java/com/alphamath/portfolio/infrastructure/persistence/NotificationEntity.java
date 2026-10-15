package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_notification")
@Data
public class NotificationEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String body;

  private String entityType;
  private String entityId;

  @Lob
  @Column(nullable = false)
  private String metadataJson;

  private Instant createdAt;
  private Instant readAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_notification_preference")
@Data
public class NotificationPreferenceEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String channel;

  @Column(nullable = false)
  private boolean enabled;

  @Lob
  @Column(nullable = false)
  private String typesJson;

  private Integer quietStartHour;
  private Integer quietEndHour;
  private String timezone;

  private Instant createdAt;
  private Instant updatedAt;
}

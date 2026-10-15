package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_notification_destination")
@Data
public class NotificationDestinationEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String channel;

  @Column(nullable = false)
  private String destination;

  private String label;

  @Column(nullable = false)
  private String status;

  private Instant createdAt;
  private Instant verifiedAt;
  private Instant updatedAt;
}

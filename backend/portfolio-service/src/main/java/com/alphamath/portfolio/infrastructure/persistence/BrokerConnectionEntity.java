package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.broker.BrokerConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_broker_connection")
@Data
public class BrokerConnectionEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String brokerId;

  @Enumerated(EnumType.STRING)
  private BrokerConnectionStatus status;

  private String label;

  @Lob
  private String metadataJson;

  private Instant createdAt;

  private Instant updatedAt;

  private Instant lastSyncedAt;
}

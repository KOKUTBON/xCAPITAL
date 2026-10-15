package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_surveillance_alert")
@Data
public class SurveillanceAlertEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String alertType;

  @Column(nullable = false)
  private String severity;

  private String symbol;
  private Double notional;
  private String detail;
  private Instant createdAt;
}

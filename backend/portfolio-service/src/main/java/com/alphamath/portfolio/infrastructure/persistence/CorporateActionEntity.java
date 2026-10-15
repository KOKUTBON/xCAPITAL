package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.reporting.CorporateActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "portfolio_corporate_action")
@Data
public class CorporateActionEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  private String accountId;

  @Enumerated(EnumType.STRING)
  private CorporateActionType actionType;

  private String symbol;

  private String instrumentId;

  private Double ratio;

  private Double cashAmount;

  private LocalDate effectiveDate;

  @Lob
  private String metadataJson;

  private Instant createdAt;
}

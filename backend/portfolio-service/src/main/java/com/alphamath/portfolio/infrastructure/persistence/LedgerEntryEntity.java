package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reporting.LedgerEntryType;
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
@Table(name = "portfolio_ledger_entry")
@Data
public class LedgerEntryEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String accountId;

  private String brokerAccountId;

  @Enumerated(EnumType.STRING)
  private LedgerEntryType entryType;

  private String symbol;

  private String instrumentId;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  private Double quantity;

  private Double price;

  private Double amount;

  private String currency;

  private Double fxRate;

  private Instant tradeDate;

  private Instant settleDate;

  @Lob
  private String description;

  @Lob
  private String metadataJson;

  private Instant createdAt;
}

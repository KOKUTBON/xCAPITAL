package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reference.InstrumentStatus;
import com.alphamath.portfolio.domain.reference.InstrumentType;
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
@Table(name = "portfolio_instrument")
@Data
public class InstrumentEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String symbol;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  @Enumerated(EnumType.STRING)
  private InstrumentType instrumentType;

  private String exchangeCode;

  @Column(nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  private InstrumentStatus status;

  @Lob
  private String externalIdsJson;

  @Lob
  private String metadataJson;

  private Instant createdAt;

  private Instant updatedAt;
}

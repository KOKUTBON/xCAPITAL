package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.AssetClass;
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
@Table(name = "portfolio_watchlist_item")
@Data
public class WatchlistItemEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String watchlistId;

  @Column(nullable = false)
  private String symbol;

  private String instrumentId;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  private String notes;

  private Double aiScore;

  private String aiSummary;

  @Lob
  private String metadataJson;

  private Instant createdAt;
}

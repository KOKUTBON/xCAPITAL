package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_research_coverage")
@Data
public class ResearchCoverageEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String symbol;

  @Column(nullable = false)
  private String rating;

  private Double priceTarget;

  @Column(nullable = false)
  private boolean focusList;

  private String analyst;

  private String summary;

  @Column(nullable = false)
  private String source;

  private Instant publishedAt;

  private Instant createdAt;
}

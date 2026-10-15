package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_research_note")
@Data
public class ResearchNoteEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String source;

  @Column(nullable = false)
  private String headline;

  @Lob
  private String summary;

  @Lob
  private String symbolsJson;

  private Double sentimentScore;

  private Double confidence;

  private Double aiScore;

  @Lob
  private String aiSummary;

  private Instant publishedAt;

  @Lob
  private String metadataJson;

  private Instant createdAt;
}

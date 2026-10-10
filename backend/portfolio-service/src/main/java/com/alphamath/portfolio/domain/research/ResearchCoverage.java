package com.alphamath.portfolio.domain.research;

import lombok.Data;

import java.time.Instant;

@Data
public class ResearchCoverage {
  private String id;
  private String symbol;
  private ResearchRating rating;
  private Double priceTarget;
  private boolean focusList;
  private String analyst;
  private String summary;
  private String source;
  private Instant publishedAt;
  private Instant createdAt;
}

package com.alphamath.portfolio.domain.research;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ResearchNote {
  private String id;
  @JsonIgnore
  private String userId;
  private String source;
  private String headline;
  private String summary;
  private List<String> symbols = new ArrayList<>();
  private Double sentimentScore;
  private Double confidence;
  private Double aiScore;
  private String aiSummary;
  private Instant publishedAt;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
}

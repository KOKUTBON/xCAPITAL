package com.alphamath.portfolio.domain.research;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ResearchNoteRequest {
  private String source;
  private String headline;
  private String summary;
  private List<String> symbols = new ArrayList<>();
  private Double sentimentScore;
  private Double confidence;
  private Instant publishedAt;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}

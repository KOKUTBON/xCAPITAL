package com.alphamath.portfolio.domain.watchlist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class Watchlist {
  private String id;
  @JsonIgnore
  private String userId;
  private String name;
  private String description;
  private Instant createdAt;
  private Instant updatedAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_paper_account")
@Data
public class PaperAccountEntity {
  @Id
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Lob
  @Column(nullable = false)
  private String payload;

  private Instant updatedAt;
}

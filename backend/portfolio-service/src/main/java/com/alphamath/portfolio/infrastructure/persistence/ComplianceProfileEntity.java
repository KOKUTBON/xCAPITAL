package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.compliance.AccountType;
import com.alphamath.portfolio.domain.compliance.AmlStatus;
import com.alphamath.portfolio.domain.compliance.KycStatus;
import com.alphamath.portfolio.domain.compliance.RiskProfile;
import com.alphamath.portfolio.domain.compliance.SuitabilityStatus;
import com.alphamath.portfolio.domain.execution.Region;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_compliance_profile")
@Data
public class ComplianceProfileEntity {
  @Id
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Enumerated(EnumType.STRING)
  private KycStatus kycStatus;

  @Enumerated(EnumType.STRING)
  private AmlStatus amlStatus;

  @Enumerated(EnumType.STRING)
  private SuitabilityStatus suitabilityStatus;

  @Enumerated(EnumType.STRING)
  private RiskProfile riskProfile;

  @Enumerated(EnumType.STRING)
  private AccountType accountType;

  @Enumerated(EnumType.STRING)
  private Region taxResidency;

  private boolean accreditedInvestor;

  @Column(name = "restrictions_json", nullable = false)
  private String restrictionsJson;

  private Instant updatedAt;
  private Instant createdAt;
}

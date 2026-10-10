package com.alphamath.portfolio.domain.compliance;

import com.alphamath.portfolio.domain.execution.Region;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ComplianceProfile {
  @JsonIgnore
  private String userId;
  private KycStatus kycStatus = KycStatus.NOT_STARTED;
  private AmlStatus amlStatus = AmlStatus.NOT_SCREENED;
  private SuitabilityStatus suitabilityStatus = SuitabilityStatus.UNKNOWN;
  private RiskProfile riskProfile = RiskProfile.MODERATE;
  private AccountType accountType = AccountType.INDIVIDUAL;
  private Region taxResidency = Region.US;
  private boolean accreditedInvestor = false;
  private List<String> restrictions = new ArrayList<>();
  private Instant updatedAt = Instant.now();
}

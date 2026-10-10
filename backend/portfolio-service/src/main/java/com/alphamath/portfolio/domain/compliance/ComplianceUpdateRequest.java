package com.alphamath.portfolio.domain.compliance;

import com.alphamath.portfolio.domain.execution.Region;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ComplianceUpdateRequest {
  private KycStatus kycStatus;
  private AmlStatus amlStatus;
  private SuitabilityStatus suitabilityStatus;
  private RiskProfile riskProfile;
  private AccountType accountType;
  private Region taxResidency;
  private Boolean accreditedInvestor;
  private List<String> restrictions = new ArrayList<>();
}

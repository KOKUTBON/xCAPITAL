package com.alphamath.portfolio.domain.funding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FundingProviderInfo {
  private String id;
  private String displayName;
  private List<FundingMethodType> methods = new ArrayList<>();
  private List<String> verificationModes = new ArrayList<>();
  private List<String> regions = new ArrayList<>();
  private String notes;
}

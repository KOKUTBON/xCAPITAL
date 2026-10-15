package com.alphamath.portfolio.domain.execution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutionIntent {
  private String id;
  @JsonIgnore
  private String userId;
  private String proposalId;
  private String providerId;
  private String providerName;
  private Region region;
  private AssetClass assetClass;
  private ExecutionStatus status;
  private List<ExecutionOrder> orders = new ArrayList<>();
  private List<String> brokerOrderIds = new ArrayList<>();
  private Instant createdAt;
  private String note;
}

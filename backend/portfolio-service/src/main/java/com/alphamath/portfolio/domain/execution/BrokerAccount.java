package com.alphamath.portfolio.domain.execution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BrokerAccount {
  private String id;
  @JsonIgnore
  private String userId;
  private String providerId;
  private String providerName;
  private String brokerConnectionId;
  private String externalAccountId;
  private String accountNumber;
  private String baseCurrency;
  private BrokerAccountType accountType;
  private List<String> permissions = new ArrayList<>();
  private Map<String, Double> balances = new LinkedHashMap<>();
  private Region region;
  private List<AssetClass> assetClasses = new ArrayList<>();
  private BrokerAccountStatus status;
  private Instant createdAt;
  private Instant updatedAt;
}

package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.BrokerAccountStatus;
import com.alphamath.portfolio.domain.execution.BrokerAccountType;
import com.alphamath.portfolio.domain.execution.Region;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_broker_account")
@Data
public class BrokerAccountEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String providerId;

  @Column(nullable = false)
  private String providerName;

  private String brokerConnectionId;

  private String externalAccountId;

  private String accountNumber;

  private String baseCurrency;

  @Enumerated(EnumType.STRING)
  private BrokerAccountType accountType;

  @Enumerated(EnumType.STRING)
  private Region region;

  @Column(name = "asset_classes_json", nullable = false)
  private String assetClassesJson;

  private String permissionsJson;

  private String balancesJson;

  @Enumerated(EnumType.STRING)
  private BrokerAccountStatus status;

  private Instant createdAt;

  private Instant updatedAt;
}

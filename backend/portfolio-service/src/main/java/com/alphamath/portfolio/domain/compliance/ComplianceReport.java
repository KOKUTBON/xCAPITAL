package com.alphamath.portfolio.domain.compliance;

import com.alphamath.portfolio.domain.broker.BrokerConnection;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.funding.FundingSource;
import com.alphamath.portfolio.domain.trade.PolicyCheck;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ComplianceReport {
  private String userId;
  private ComplianceProfile profile;
  private List<PolicyCheck> checks = new ArrayList<>();
  private List<FundingSource> fundingSources = new ArrayList<>();
  private List<BrokerConnection> brokerConnections = new ArrayList<>();
  private List<BrokerAccount> brokerAccounts = new ArrayList<>();
  private Instant generatedAt;
}

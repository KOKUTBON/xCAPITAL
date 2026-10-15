package com.alphamath.portfolio.application.compliance;

import com.alphamath.portfolio.application.broker.BrokerIntegrationService;
import com.alphamath.portfolio.application.funding.FundingService;
import com.alphamath.portfolio.domain.compliance.ComplianceReport;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ComplianceReportService {
  private final ComplianceService compliance;
  private final FundingService funding;
  private final BrokerIntegrationService brokers;

  public ComplianceReportService(ComplianceService compliance,
                                 FundingService funding,
                                 BrokerIntegrationService brokers) {
    this.compliance = compliance;
    this.funding = funding;
    this.brokers = brokers;
  }

  public ComplianceReport report(String userId) {
    ComplianceReport report = new ComplianceReport();
    report.setUserId(userId);
    report.setProfile(compliance.getProfile(userId));
    report.setChecks(compliance.complianceChecks(userId));
    report.setFundingSources(funding.listSources(userId));
    report.setBrokerConnections(brokers.listConnections(userId));
    report.setBrokerAccounts(brokers.listAccounts(userId));
    report.setGeneratedAt(Instant.now());
    return report;
  }
}

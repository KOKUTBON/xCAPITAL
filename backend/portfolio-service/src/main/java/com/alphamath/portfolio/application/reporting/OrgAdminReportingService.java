package com.alphamath.portfolio.application.reporting;

import com.alphamath.portfolio.infrastructure.persistence.AuditEventEntity;
import com.alphamath.portfolio.infrastructure.persistence.AuditEventRepository;
import com.alphamath.portfolio.infrastructure.persistence.AutoInvestPlanRepository;
import com.alphamath.portfolio.infrastructure.persistence.BankingAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.ComplianceProfileRepository;
import com.alphamath.portfolio.infrastructure.persistence.CorporateActionRepository;
import com.alphamath.portfolio.infrastructure.persistence.FundingSourceRepository;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataEntitlementRepository;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataLicenseRepository;
import com.alphamath.portfolio.infrastructure.persistence.NotificationRepository;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.ResearchNoteRepository;
import com.alphamath.portfolio.infrastructure.persistence.RewardEnrollmentRepository;
import com.alphamath.portfolio.infrastructure.persistence.StatementRepository;
import com.alphamath.portfolio.infrastructure.persistence.SurveillanceAlertRepository;
import com.alphamath.portfolio.infrastructure.persistence.TaxLotRepository;
import com.alphamath.portfolio.infrastructure.persistence.WatchlistRepository;
import com.alphamath.portfolio.infrastructure.persistence.WealthPlanRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrgAdminReportingService {
  private final TenantContext tenantContext;
  private final PortfolioAccountRepository portfolioAccounts;
  private final BankingAccountRepository bankingAccounts;
  private final FundingSourceRepository fundingSources;
  private final BrokerAccountRepository brokerAccounts;
  private final AutoInvestPlanRepository autoInvestPlans;
  private final WealthPlanRepository wealthPlans;
  private final WatchlistRepository watchlists;
  private final RewardEnrollmentRepository rewards;
  private final NotificationRepository notifications;
  private final MarketDataLicenseRepository marketDataLicenses;
  private final MarketDataEntitlementRepository marketDataEntitlements;
  private final StatementRepository statements;
  private final ResearchNoteRepository researchNotes;
  private final CorporateActionRepository corporateActions;
  private final TaxLotRepository taxLots;
  private final SurveillanceAlertRepository surveillance;
  private final ComplianceProfileRepository complianceProfiles;
  private final AuditEventRepository auditEvents;

  public OrgAdminReportingService(TenantContext tenantContext,
                                  PortfolioAccountRepository portfolioAccounts,
                                  BankingAccountRepository bankingAccounts,
                                  FundingSourceRepository fundingSources,
                                  BrokerAccountRepository brokerAccounts,
                                  AutoInvestPlanRepository autoInvestPlans,
                                  WealthPlanRepository wealthPlans,
                                  WatchlistRepository watchlists,
                                  RewardEnrollmentRepository rewards,
                                  NotificationRepository notifications,
                                  MarketDataLicenseRepository marketDataLicenses,
                                  MarketDataEntitlementRepository marketDataEntitlements,
                                  StatementRepository statements,
                                  ResearchNoteRepository researchNotes,
                                  CorporateActionRepository corporateActions,
                                  TaxLotRepository taxLots,
                                  SurveillanceAlertRepository surveillance,
                                  ComplianceProfileRepository complianceProfiles,
                                  AuditEventRepository auditEvents) {
    this.tenantContext = tenantContext;
    this.portfolioAccounts = portfolioAccounts;
    this.bankingAccounts = bankingAccounts;
    this.fundingSources = fundingSources;
    this.brokerAccounts = brokerAccounts;
    this.autoInvestPlans = autoInvestPlans;
    this.wealthPlans = wealthPlans;
    this.watchlists = watchlists;
    this.rewards = rewards;
    this.notifications = notifications;
    this.marketDataLicenses = marketDataLicenses;
    this.marketDataEntitlements = marketDataEntitlements;
    this.statements = statements;
    this.researchNotes = researchNotes;
    this.corporateActions = corporateActions;
    this.taxLots = taxLots;
    this.surveillance = surveillance;
    this.complianceProfiles = complianceProfiles;
    this.auditEvents = auditEvents;
  }

  public Map<String, Object> summary() {
    String orgId = requireOrgId();

    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("portfolioAccounts", portfolioAccounts.countByOrgId(orgId));
    counts.put("bankingAccounts", bankingAccounts.countByOrgId(orgId));
    counts.put("fundingSources", fundingSources.countByOrgId(orgId));
    counts.put("brokerAccounts", brokerAccounts.countByOrgId(orgId));
    counts.put("autoInvestPlans", autoInvestPlans.countByOrgId(orgId));
    counts.put("wealthPlans", wealthPlans.countByOrgId(orgId));
    counts.put("watchlists", watchlists.countByOrgId(orgId));
    counts.put("rewardEnrollments", rewards.countByOrgId(orgId));
    counts.put("notifications", notifications.countByOrgId(orgId));
    counts.put("marketDataLicenses", marketDataLicenses.countByOrgId(orgId));
    counts.put("marketDataEntitlements", marketDataEntitlements.countByOrgId(orgId));
    counts.put("statements", statements.countByOrgId(orgId));
    counts.put("researchNotes", researchNotes.countByOrgId(orgId));
    counts.put("corporateActions", corporateActions.countByOrgId(orgId));
    counts.put("taxLots", taxLots.countByOrgId(orgId));
    counts.put("surveillanceAlerts", surveillance.countByOrgId(orgId));
    counts.put("complianceProfiles", complianceProfiles.countByOrgId(orgId));
    counts.put("auditEvents", auditEvents.countByOrgId(orgId));

    Map<String, Object> balances = new LinkedHashMap<>();
    balances.put("portfolioCash", safeAmount(portfolioAccounts.sumCashByOrgId(orgId)));
    balances.put("bankingCash", safeAmount(bankingAccounts.sumCashByOrgId(orgId)));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("orgId", orgId);
    out.put("generatedAt", Instant.now());
    out.put("counts", counts);
    out.put("balances", balances);
    out.put("recentAuditEvents", recentAudit(20));
    return out;
  }

  public List<Map<String, Object>> recentAudit(int limit) {
    String orgId = requireOrgId();
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    return auditEvents.findByOrgIdOrderByCreatedAtDesc(orgId, PageRequest.of(0, size))
        .stream()
        .map(this::toAuditSnapshot)
        .toList();
  }

  private String requireOrgId() {
    String orgId = tenantContext.getOrgId();
    if (orgId == null || orgId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Org-Id header is required");
    }
    return orgId;
  }

  private double safeAmount(Double value) {
    return value == null ? 0.0 : value;
  }

  private Map<String, Object> toAuditSnapshot(AuditEventEntity row) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", row.getId());
    out.put("actor", row.getActor());
    out.put("eventType", row.getEventType());
    out.put("entityType", row.getEntityType());
    out.put("entityId", row.getEntityId());
    out.put("createdAt", row.getCreatedAt());
    return out;
  }
}

package com.alphamath.portfolio.application.reporting;

import com.alphamath.portfolio.domain.reporting.LedgerEntryRequest;
import com.alphamath.portfolio.domain.reporting.ReconciliationReport;
import com.alphamath.portfolio.domain.reporting.ReconciliationRequest;
import com.alphamath.portfolio.domain.reporting.StatementFeedRequest;
import com.alphamath.portfolio.domain.reporting.StatementImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StatementFeedService {
  private static final int BATCH_SIZE = 500;

  private final List<StatementFeedProvider> providers;
  private final ReportingService reporting;
  private final ReconciliationService reconciliation;

  public StatementFeedService(List<StatementFeedProvider> providers,
                              ReportingService reporting,
                              ReconciliationService reconciliation) {
    this.providers = providers == null ? List.of() : providers;
    this.reporting = reporting;
    this.reconciliation = reconciliation;
  }

  public StatementImportResult importFeed(String userId, StatementFeedRequest req) {
    if (req == null || req.getProviderId() == null || req.getProviderId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId required");
    }
    if (req.getAccountId() == null || req.getAccountId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    StatementFeedProvider provider = providerFor(req.getProviderId());
    List<LedgerEntryRequest> entries = provider.fetch(
        req.getProviderId(),
        req.getAccountId(),
        req.getStart(),
        req.getEnd(),
        req.getMetadata()
    );

    StatementImportResult result = new StatementImportResult();
    result.setAccountId(req.getAccountId());
    result.setSource(req.getProviderId());
    result.setImportedAt(Instant.now());

    int totalRows = entries.size();
    int ingestedRows = 0;
    List<LedgerEntryRequest> batch = new ArrayList<>(BATCH_SIZE);
    for (LedgerEntryRequest entry : entries) {
      batch.add(entry);
      if (batch.size() >= BATCH_SIZE) {
        ingestedRows += reporting.addLedgerEntries(userId, batch).size();
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      ingestedRows += reporting.addLedgerEntries(userId, batch).size();
    }

    result.setTotalRows(totalRows);
    result.setIngestedRows(ingestedRows);
    result.setFailedRows(totalRows - ingestedRows);
    result.setErrors(List.of());

    if (req.isApplyPositions() || req.isRebuildTaxLots()) {
      ReconciliationRequest reconcileReq = new ReconciliationRequest();
      reconcileReq.setAccountId(req.getAccountId());
      reconcileReq.setApplyPositions(req.isApplyPositions());
      reconcileReq.setRebuildTaxLots(req.isRebuildTaxLots());
      reconcileReq.setLotMethod(req.getLotMethod());
      ReconciliationReport report = reconciliation.reconcile(userId, reconcileReq);
      result.setReconciliation(report);
    }

    return result;
  }

  public List<String> listProviders() {
    List<String> out = new ArrayList<>();
    for (StatementFeedProvider provider : providers) {
      out.addAll(provider.providerIds());
    }
    return out;
  }

  private StatementFeedProvider providerFor(String providerId) {
    for (StatementFeedProvider provider : providers) {
      if (provider.supports(providerId)) {
        return provider;
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement feed provider not found");
  }
}

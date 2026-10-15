package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.reporting.ReconciliationService;
import com.alphamath.portfolio.application.reporting.ReportingService;
import com.alphamath.portfolio.application.reporting.StatementFeedService;
import com.alphamath.portfolio.application.reporting.StatementImportService;
import com.alphamath.portfolio.domain.reporting.CorporateAction;
import com.alphamath.portfolio.domain.reporting.CorporateActionRequest;
import com.alphamath.portfolio.domain.reporting.LedgerEntry;
import com.alphamath.portfolio.domain.reporting.LedgerEntryRequest;
import com.alphamath.portfolio.domain.reporting.ReconciliationReport;
import com.alphamath.portfolio.domain.reporting.ReconciliationRequest;
import com.alphamath.portfolio.domain.reporting.Statement;
import com.alphamath.portfolio.domain.reporting.StatementFeedRequest;
import com.alphamath.portfolio.domain.reporting.StatementImportRequest;
import com.alphamath.portfolio.domain.reporting.StatementImportResult;
import com.alphamath.portfolio.domain.reporting.StatementRequest;
import com.alphamath.portfolio.domain.reporting.TaxLot;
import com.alphamath.portfolio.domain.reporting.TaxLotRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/statements")
public class ReportingController {
  private final ReportingService reporting;
  private final ReconciliationService reconciliation;
  private final StatementImportService statementImport;
  private final StatementFeedService statementFeed;

  public ReportingController(ReportingService reporting,
                             ReconciliationService reconciliation,
                             StatementImportService statementImport,
                             StatementFeedService statementFeed) {
    this.reporting = reporting;
    this.reconciliation = reconciliation;
    this.statementImport = statementImport;
    this.statementFeed = statementFeed;
  }

  @PostMapping("/ledger")
  public List<LedgerEntry> addLedger(@RequestBody List<LedgerEntryRequest> reqs, Principal principal) {
    return reporting.addLedgerEntries(userId(principal), reqs);
  }

  @GetMapping("/ledger")
  public List<LedgerEntry> listLedger(@RequestParam String accountId,
                                      @RequestParam(required = false) Instant start,
                                      @RequestParam(required = false) Instant end,
                                      @RequestParam(required = false) Integer limit,
                                      Principal principal) {
    return reporting.listLedgerEntries(userId(principal), accountId, start, end, limit == null ? 0 : limit);
  }

  @PostMapping("/tax-lots")
  public TaxLot upsertTaxLot(@RequestBody TaxLotRequest req, Principal principal) {
    return reporting.upsertTaxLot(userId(principal), req);
  }

  @GetMapping("/tax-lots")
  public List<TaxLot> listTaxLots(@RequestParam String accountId,
                                  @RequestParam(required = false) String symbol,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Integer limit,
                                  Principal principal) {
    return reporting.listTaxLots(userId(principal), accountId, symbol, status, limit == null ? 0 : limit);
  }

  @PostMapping("/corporate-actions")
  public CorporateAction addCorporateAction(@RequestBody CorporateActionRequest req, Principal principal) {
    return reporting.addCorporateAction(userId(principal), req);
  }

  @GetMapping("/corporate-actions")
  public List<CorporateAction> listCorporateActions(@RequestParam(required = false) String symbol,
                                                    @RequestParam(required = false) Integer limit,
                                                    Principal principal) {
    return reporting.listCorporateActions(userId(principal), symbol, limit == null ? 0 : limit);
  }

  @PostMapping
  public Statement generateStatement(@RequestBody StatementRequest req, Principal principal) {
    return reporting.generateStatement(userId(principal), req);
  }

  @GetMapping
  public List<Statement> listStatements(@RequestParam String accountId,
                                        @RequestParam(required = false) Integer limit,
                                        Principal principal) {
    return reporting.listStatements(userId(principal), accountId, limit == null ? 0 : limit);
  }

  @GetMapping("/summary")
  public Map<String, Object> summary(@RequestParam String accountId,
                                     @RequestParam(required = false) Instant start,
                                     @RequestParam(required = false) Instant end,
                                     Principal principal) {
    return reporting.statementSummary(userId(principal), accountId, start, end);
  }

  @PostMapping("/reconcile")
  public ReconciliationReport reconcile(@RequestBody ReconciliationRequest req, Principal principal) {
    return reconciliation.reconcile(userId(principal), req);
  }

  @PostMapping("/import")
  public StatementImportResult importStatement(@RequestBody StatementImportRequest req, Principal principal) {
    return statementImport.importStatement(userId(principal), req);
  }

  @GetMapping("/providers")
  public List<String> statementProviders() {
    return statementFeed.listProviders();
  }

  @PostMapping("/import-feed")
  public StatementImportResult importFeed(@RequestBody StatementFeedRequest req, Principal principal) {
    return statementFeed.importFeed(userId(principal), req);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

package com.alphamath.portfolio.application.reporting;

import com.alphamath.portfolio.domain.reporting.Statement;
import com.alphamath.portfolio.domain.reporting.TaxLotRequest;
import com.alphamath.portfolio.infrastructure.persistence.CorporateActionRepository;
import com.alphamath.portfolio.infrastructure.persistence.LedgerEntryRepository;
import com.alphamath.portfolio.infrastructure.persistence.StatementRepository;
import com.alphamath.portfolio.infrastructure.persistence.TaxLotEntity;
import com.alphamath.portfolio.infrastructure.persistence.TaxLotRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceOrgScopeTest {

  @Mock
  private LedgerEntryRepository ledger;
  @Mock
  private TaxLotRepository taxLots;
  @Mock
  private CorporateActionRepository corporateActions;
  @Mock
  private StatementRepository statements;

  private TenantContext tenantContext;
  private ReportingService service;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    service = new ReportingService(ledger, taxLots, corporateActions, statements, tenantContext);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void listStatementsUsesOrgScopedRepositoryWhenOrgContextExists() {
    MDC.put("orgId", "org-1");
    when(statements.findByUserIdAndOrgIdAndAccountIdOrderByPeriodEndDesc(
        eq("user-1"), eq("org-1"), eq("acct-1"), any(PageRequest.class)))
        .thenReturn(List.of());

    List<Statement> result = service.listStatements("user-1", "acct-1", 24);

    assertTrue(result.isEmpty());
    verify(statements).findByUserIdAndOrgIdAndAccountIdOrderByPeriodEndDesc(
        eq("user-1"), eq("org-1"), eq("acct-1"), any(PageRequest.class));
    verify(statements, never()).findByUserIdAndAccountIdOrderByPeriodEndDesc(
        eq("user-1"), eq("acct-1"), any(PageRequest.class));
  }

  @Test
  void upsertTaxLotRejectsOrgMismatches() {
    MDC.put("orgId", "org-1");

    TaxLotRequest req = new TaxLotRequest();
    req.setId("lot-1");
    req.setAccountId("acct-1");
    req.setSymbol("AAPL");

    TaxLotEntity existing = new TaxLotEntity();
    existing.setId("lot-1");
    existing.setUserId("user-1");
    existing.setOrgId("org-2");
    when(taxLots.findById("lot-1")).thenReturn(Optional.of(existing));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.upsertTaxLot("user-1", req));

    assertEquals(404, ex.getStatusCode().value());
  }
}

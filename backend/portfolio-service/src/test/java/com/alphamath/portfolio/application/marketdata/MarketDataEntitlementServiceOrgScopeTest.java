package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlement;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlementRequest;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlementType;
import com.alphamath.portfolio.infrastructure.persistence.ExchangeRepository;
import com.alphamath.portfolio.infrastructure.persistence.InstrumentRepository;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataEntitlementEntity;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataEntitlementRepository;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataLicenseRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataEntitlementServiceOrgScopeTest {

  @Mock
  private MarketDataLicenseRepository licenses;
  @Mock
  private MarketDataEntitlementRepository entitlements;
  @Mock
  private InstrumentRepository instruments;
  @Mock
  private ExchangeRepository exchanges;

  private TenantContext tenantContext;
  private MarketDataEntitlementService service;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    service = new MarketDataEntitlementService(
        licenses, entitlements, instruments, exchanges, new MarketDataEntitlementProperties(), tenantContext);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void listEntitlementsUsesOrgScopedStatusQueryWhenOrgContextExists() {
    MDC.put("orgId", "org-1");
    when(entitlements.findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc("user-1", "org-1", "ACTIVE"))
        .thenReturn(List.of());

    List<MarketDataEntitlement> result = service.listEntitlements("user-1", "active");

    assertTrue(result.isEmpty());
    verify(entitlements).findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc("user-1", "org-1", "ACTIVE");
    verify(entitlements, never()).findByUserIdAndStatusOrderByCreatedAtDesc("user-1", "ACTIVE");
  }

  @Test
  void upsertEntitlementRejectsOrgMismatches() {
    MDC.put("orgId", "org-1");

    MarketDataEntitlementRequest req = new MarketDataEntitlementRequest();
    req.setId("ent-1");
    req.setEntitlementType(MarketDataEntitlementType.SYMBOL);
    req.setEntitlementValue("AAPL");

    MarketDataEntitlementEntity existing = new MarketDataEntitlementEntity();
    existing.setId("ent-1");
    existing.setUserId("user-1");
    existing.setOrgId("org-2");
    when(entitlements.findById("ent-1")).thenReturn(Optional.of(existing));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.upsertEntitlement("user-1", req));

    assertEquals(404, ex.getStatusCode().value());
  }
}

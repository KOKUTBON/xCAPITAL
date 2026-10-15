package com.alphamath.portfolio.application.research;

import com.alphamath.portfolio.domain.research.ResearchNote;
import com.alphamath.portfolio.infrastructure.persistence.ResearchNoteEntity;
import com.alphamath.portfolio.infrastructure.persistence.ResearchNoteRepository;
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
class ResearchServiceOrgScopeTest {

  @Mock
  private ResearchNoteRepository notes;

  private TenantContext tenantContext;
  private ResearchService service;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    service = new ResearchService(notes, null, null, null, tenantContext);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void listUsesOrgScopedSourceQueryWhenOrgContextExists() {
    MDC.put("orgId", "org-1");
    when(notes.findByUserIdAndOrgIdAndSourceOrderByPublishedAtDesc(
        eq("user-1"), eq("org-1"), eq("Reuters"), any(PageRequest.class)))
        .thenReturn(List.of());

    List<ResearchNote> result = service.list("user-1", "Reuters", 50);

    assertTrue(result.isEmpty());
    verify(notes).findByUserIdAndOrgIdAndSourceOrderByPublishedAtDesc(
        eq("user-1"), eq("org-1"), eq("Reuters"), any(PageRequest.class));
    verify(notes, never()).findByUserIdAndSourceOrderByPublishedAtDesc(
        eq("user-1"), eq("Reuters"), any(PageRequest.class));
  }

  @Test
  void refreshAiRejectsOrgMismatches() {
    MDC.put("orgId", "org-1");

    ResearchNoteEntity existing = new ResearchNoteEntity();
    existing.setId("note-1");
    existing.setUserId("user-1");
    existing.setOrgId("org-2");
    when(notes.findById("note-1")).thenReturn(Optional.of(existing));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.refreshAi("user-1", "note-1", 120, 1));

    assertEquals(404, ex.getStatusCode().value());
  }
}

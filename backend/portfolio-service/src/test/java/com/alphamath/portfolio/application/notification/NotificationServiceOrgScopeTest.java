package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.infrastructure.persistence.NotificationEntity;
import com.alphamath.portfolio.infrastructure.persistence.NotificationRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.slf4j.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
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
class NotificationServiceOrgScopeTest {

  @Mock
  private NotificationRepository notifications;

  private TenantContext tenantContext;
  private NotificationService service;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    service = new NotificationService(notifications, null, tenantContext);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void listUsesOrgScopedStatusQueryWhenOrgContextExists() {
    MDC.put("orgId", "org-1");
    when(notifications.findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(
        eq("user-1"), eq("org-1"), eq("UNREAD"), any(Pageable.class)))
        .thenReturn(List.of());

    List<Notification> result = service.list("user-1", "unread", 20);

    assertTrue(result.isEmpty());
    verify(notifications).findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(
        eq("user-1"), eq("org-1"), eq("UNREAD"), any(Pageable.class));
    verify(notifications, never()).findByUserIdAndStatusOrderByCreatedAtDesc(
        eq("user-1"), eq("UNREAD"), any(Pageable.class));
  }

  @Test
  void markReadRejectsNotificationOutsideOrgContext() {
    MDC.put("orgId", "org-1");

    NotificationEntity row = new NotificationEntity();
    row.setId("note-1");
    row.setUserId("user-1");
    row.setOrgId("org-2");
    row.setStatus("UNREAD");
    when(notifications.findById("note-1")).thenReturn(Optional.of(row));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.markRead("user-1", "note-1"));

    assertEquals(404, ex.getStatusCode().value());
  }
}

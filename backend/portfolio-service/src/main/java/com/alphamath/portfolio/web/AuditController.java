package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.domain.audit.AuditEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
  private final AuditService audit;

  public AuditController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping("/events")
  public List<AuditEvent> events(Principal principal,
                                 @RequestParam(required = false) String eventType,
                                 @RequestParam(required = false) String entityId,
                                 @RequestParam(required = false, defaultValue = "50") int limit) {
    return audit.list(userId(principal), eventType, entityId, limit);
  }

  @GetMapping("/events/export")
  public ResponseEntity<String> export(Principal principal,
                                       @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                       @RequestParam(required = false) String eventType,
                                       @RequestParam(required = false) String entityId,
                                       @RequestParam(required = false, defaultValue = "500") int limit) {
    requireRole(roles, "ADMIN", "AUDITOR");
    String payload = audit.exportCsv(userId(principal), eventType, entityId, limit);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_export.csv")
        .body(payload);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  private void requireRole(String rolesHeader, String... allowed) {
    if (rolesHeader == null || rolesHeader.isBlank()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role");
    }
    String normalized = rolesHeader.toUpperCase(Locale.US);
    for (String role : allowed) {
      if (normalized.contains(role.toUpperCase(Locale.US))) {
        return;
      }
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role");
  }
}

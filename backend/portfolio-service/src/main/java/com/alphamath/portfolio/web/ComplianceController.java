package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.compliance.ComplianceReportService;
import com.alphamath.portfolio.application.compliance.ComplianceService;
import com.alphamath.portfolio.domain.compliance.ComplianceProfile;
import com.alphamath.portfolio.domain.compliance.ComplianceReport;
import com.alphamath.portfolio.domain.compliance.ComplianceUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceController {
  private final ComplianceService compliance;
  private final ComplianceReportService reports;

  public ComplianceController(ComplianceService compliance, ComplianceReportService reports) {
    this.compliance = compliance;
    this.reports = reports;
  }

  @GetMapping("/profile")
  public ComplianceProfile profile(Principal principal) {
    return compliance.getProfile(userId(principal));
  }

  @PostMapping("/profile")
  public ComplianceProfile update(@Valid @RequestBody ComplianceUpdateRequest req, Principal principal) {
    return compliance.updateProfile(userId(principal), req);
  }

  @GetMapping("/report")
  public ComplianceReport report(Principal principal) {
    return reports.report(userId(principal));
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

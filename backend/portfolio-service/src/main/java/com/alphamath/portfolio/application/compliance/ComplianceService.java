package com.alphamath.portfolio.application.compliance;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.domain.compliance.AmlStatus;
import com.alphamath.portfolio.domain.compliance.ComplianceProfile;
import com.alphamath.portfolio.domain.compliance.ComplianceUpdateRequest;
import com.alphamath.portfolio.domain.compliance.KycStatus;
import com.alphamath.portfolio.domain.compliance.SuitabilityStatus;
import com.alphamath.portfolio.domain.trade.CheckStatus;
import com.alphamath.portfolio.domain.trade.PolicyCheck;
import com.alphamath.portfolio.infrastructure.persistence.ComplianceProfileEntity;
import com.alphamath.portfolio.infrastructure.persistence.ComplianceProfileRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComplianceService {
  private final ComplianceProfileRepository profiles;
  private final AuditService audit;
  private final TenantContext tenantContext;

  public ComplianceService(ComplianceProfileRepository profiles, AuditService audit, TenantContext tenantContext) {
    this.profiles = profiles;
    this.audit = audit;
    this.tenantContext = tenantContext;
  }

  public ComplianceProfile getProfile(String userId) {
    String orgId = tenantContext.getOrgId();
    ComplianceProfileEntity entity = profiles.findById(userId).orElseGet(() -> {
      ComplianceProfile profile = new ComplianceProfile();
      ComplianceProfileEntity created = profiles.save(toEntity(userId, profile, null));
      audit.record(userId, userId, "COMPLIANCE_CREATED", "portfolio_compliance_profile", userId, java.util.Map.of());
      return created;
    });
    if (!tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Compliance profile not in org");
    }
    return fromEntity(entity);
  }

  @Transactional
  public ComplianceProfile updateProfile(String userId, ComplianceUpdateRequest req) {
    ComplianceProfileEntity entity = profiles.findById(userId).orElseGet(() -> {
      ComplianceProfile profile = new ComplianceProfile();
      return toEntity(userId, profile, null);
    });
    String orgId = tenantContext.getOrgId();
    if (!tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Compliance profile not in org");
    }
    Instant createdAt = entity.getCreatedAt();
    ComplianceProfile profile = fromEntity(entity);
    if (req.getKycStatus() != null) profile.setKycStatus(req.getKycStatus());
    if (req.getAmlStatus() != null) profile.setAmlStatus(req.getAmlStatus());
    if (req.getSuitabilityStatus() != null) profile.setSuitabilityStatus(req.getSuitabilityStatus());
    if (req.getRiskProfile() != null) profile.setRiskProfile(req.getRiskProfile());
    if (req.getAccountType() != null) profile.setAccountType(req.getAccountType());
    if (req.getTaxResidency() != null) profile.setTaxResidency(req.getTaxResidency());
    if (req.getAccreditedInvestor() != null) profile.setAccreditedInvestor(req.getAccreditedInvestor());
    if (req.getRestrictions() != null) profile.setRestrictions(new ArrayList<>(req.getRestrictions()));
    profile.setUpdatedAt(Instant.now());
    profiles.save(toEntity(userId, profile, createdAt));
    audit.record(userId, userId, "COMPLIANCE_UPDATED", "portfolio_compliance_profile", userId, java.util.Map.of(
        "kycStatus", profile.getKycStatus().name(),
        "amlStatus", profile.getAmlStatus().name(),
        "suitabilityStatus", profile.getSuitabilityStatus().name(),
        "riskProfile", profile.getRiskProfile().name()
    ));
    return profile;
  }

  public List<PolicyCheck> complianceChecks(String userId) {
    ComplianceProfile profile = getProfile(userId);
    List<PolicyCheck> checks = new ArrayList<>();
    checks.add(check("KYC", profile.getKycStatus() == KycStatus.VERIFIED,
        "Status=" + profile.getKycStatus()));
    checks.add(check("AML", profile.getAmlStatus() == AmlStatus.PASSED,
        "Status=" + profile.getAmlStatus()));
    checks.add(check("Suitability", profile.getSuitabilityStatus() == SuitabilityStatus.SUITABLE,
        "Status=" + profile.getSuitabilityStatus()));
    if (!profile.getRestrictions().isEmpty()) {
      checks.add(check("Restrictions", false, "Restrictions=" + profile.getRestrictions()));
    } else {
      checks.add(check("Restrictions", true, "No restrictions"));
    }
    return checks;
  }

  public void requireLiveEligibility(String userId) {
    ComplianceProfile profile = getProfile(userId);
    if (profile.getKycStatus() != KycStatus.VERIFIED) {
      throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "KYC not verified");
    }
    if (profile.getAmlStatus() != AmlStatus.PASSED) {
      throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AML not cleared");
    }
    if (profile.getSuitabilityStatus() != SuitabilityStatus.SUITABLE) {
      throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Suitability not cleared");
    }
    if (!profile.getRestrictions().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Account has restrictions");
    }
  }

  private PolicyCheck check(String name, boolean pass, String detail) {
    PolicyCheck c = new PolicyCheck();
    c.setName(name);
    c.setStatus(pass ? CheckStatus.PASS : CheckStatus.FAIL);
    c.setDetail(detail);
    return c;
  }

  private ComplianceProfile fromEntity(ComplianceProfileEntity entity) {
    ComplianceProfile profile = new ComplianceProfile();
    profile.setUserId(entity.getUserId());
    profile.setKycStatus(entity.getKycStatus());
    profile.setAmlStatus(entity.getAmlStatus());
    profile.setSuitabilityStatus(entity.getSuitabilityStatus());
    profile.setRiskProfile(entity.getRiskProfile());
    profile.setAccountType(entity.getAccountType());
    profile.setTaxResidency(entity.getTaxResidency());
    profile.setAccreditedInvestor(entity.isAccreditedInvestor());
    profile.setRestrictions(parseRestrictions(entity.getRestrictionsJson()));
    profile.setUpdatedAt(entity.getUpdatedAt());
    return profile;
  }

  private ComplianceProfileEntity toEntity(String userId, ComplianceProfile profile, Instant createdAt) {
    ComplianceProfileEntity entity = new ComplianceProfileEntity();
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setKycStatus(profile.getKycStatus());
    entity.setAmlStatus(profile.getAmlStatus());
    entity.setSuitabilityStatus(profile.getSuitabilityStatus());
    entity.setRiskProfile(profile.getRiskProfile());
    entity.setAccountType(profile.getAccountType());
    entity.setTaxResidency(profile.getTaxResidency());
    entity.setAccreditedInvestor(profile.isAccreditedInvestor());
    entity.setRestrictionsJson(JsonUtils.toJson(profile.getRestrictions()));
    entity.setUpdatedAt(profile.getUpdatedAt());
    entity.setCreatedAt(createdAt == null ? Instant.now() : createdAt);
    return entity;
  }

  private List<String> parseRestrictions(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    return JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
  }
}

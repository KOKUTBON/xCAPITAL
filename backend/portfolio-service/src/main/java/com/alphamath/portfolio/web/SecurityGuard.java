package com.alphamath.portfolio.web;

import com.alphamath.portfolio.security.AuthenticatedRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Component
public class SecurityGuard {
  private final SecurityPolicyProperties properties;

  public SecurityGuard(SecurityPolicyProperties properties) {
    this.properties = properties;
  }

  public void requireMfa(String mfaHeader, String reason) {
    if (properties == null || properties.getMfa() == null || !properties.getMfa().isEnforce()) {
      return;
    }
    if (!isMfaVerified(mfaHeader)) {
      String message = reason == null || reason.isBlank()
          ? "MFA required"
          : "MFA required for " + reason;
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
  }

  public void requireRole(String rolesHeader, String... allowedRoles) {
    if (properties == null || properties.getRbac() == null || !properties.getRbac().isEnforce()) {
      return;
    }
    if (!hasAnyRole(resolveUserRoles(rolesHeader), allowedRoles)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role");
    }
  }

  public void requireOrgRole(String... allowedRoles) {
    // Org-admin endpoints always require org role checks.
    if (!hasAnyRole(resolveOrgRoles(), allowedRoles)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role");
    }
  }

  private boolean isMfaVerified(String header) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getDetails() instanceof AuthenticatedRequestContext context
        && context.mfaVerified();
  }

  private Set<String> resolveUserRoles(String rolesHeader) {
    return trustedRoles(false);
  }

  private Set<String> resolveOrgRoles() {
    return trustedRoles(true);
  }

  private Set<String> trustedRoles(boolean orgRoles) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return Set.of();
    }
    Object details = authentication.getDetails();
    if (!(details instanceof AuthenticatedRequestContext context)) {
      return Set.of();
    }
    Set<String> roles = orgRoles ? context.orgRoles() : context.roles();
    return roles == null ? Set.of() : roles;
  }

  private boolean hasAnyRole(Set<String> roles, String... allowedRoles) {
    if (allowedRoles == null || allowedRoles.length == 0) {
      return true;
    }
    if (roles == null || roles.isEmpty()) {
      return false;
    }
    for (String allowed : allowedRoles) {
      if (allowed == null) continue;
      String normalized = allowed.trim().toUpperCase(Locale.US);
      if (!normalized.isEmpty() && roles.contains(normalized)) {
        return true;
      }
    }
    return false;
  }
}

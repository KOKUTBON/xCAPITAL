package com.alphamath.portfolio.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record AuthenticatedRequestContext(
    String userId,
    String email,
    String orgId,
    Set<String> roles,
    Set<String> orgRoles,
    boolean mfaVerified
) {
  public AuthenticatedRequestContext(String userId, String email, String orgId, Set<String> roles, Set<String> orgRoles) {
    this(userId, email, orgId, roles, orgRoles, false);
  }
  public AuthenticatedRequestContext {
    userId = normalizeValue(userId);
    email = normalizeValue(email);
    orgId = normalizeValue(orgId);
    roles = normalizeRoles(roles);
    orgRoles = normalizeRoles(orgRoles);
  }

  private static String normalizeValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static Set<String> normalizeRoles(Set<String> source) {
    if (source == null || source.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String role : source) {
      if (role == null) {
        continue;
      }
      String candidate = role.trim().toUpperCase(Locale.US);
      if (!candidate.isEmpty()) {
        normalized.add(candidate);
      }
    }
    if (normalized.isEmpty()) {
      return Set.of();
    }
    return Collections.unmodifiableSet(normalized);
  }
}

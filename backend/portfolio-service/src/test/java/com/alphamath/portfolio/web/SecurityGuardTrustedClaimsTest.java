package com.alphamath.portfolio.web;

import com.alphamath.portfolio.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SecurityGuardTrustedClaimsTest {
  final SecurityPolicyProperties policy = new SecurityPolicyProperties();
  final SecurityGuard guard = new SecurityGuard(policy);
  @BeforeEach void setup() { policy.getMfa().setEnforce(true); policy.getRbac().setEnforce(true); }
  @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
  void authenticate(boolean mfa, Set<String> roles) {
    var auth = new UsernamePasswordAuthenticationToken("user", null, Set.of());
    auth.setDetails(new AuthenticatedRequestContext("user", "test@example.org", "org", roles, Set.of(), mfa));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
  @Test void headerCannotGrantRoleWithoutAuthentication() {
    assertThrows(ResponseStatusException.class, () -> guard.requireRole("ADMIN", "ADMIN"));
  }
  @Test void spoofedMfaHeaderCannotSatisfyPolicy() {
    authenticate(false, Set.of("USER"));
    assertThrows(ResponseStatusException.class, () -> guard.requireMfa("true", "execution"));
  }
  @Test void signedMfaContextSatisfiesPolicyWithoutHeader() {
    authenticate(true, Set.of("USER")); assertDoesNotThrow(() -> guard.requireMfa(null, "execution"));
  }
  @Test void emptySignedRolesCannotBeReplacedByHeader() {
    authenticate(true, Set.of()); assertThrows(ResponseStatusException.class, () -> guard.requireRole("ADMIN", "ADMIN"));
  }
}

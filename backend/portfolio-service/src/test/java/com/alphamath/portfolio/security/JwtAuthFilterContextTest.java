package com.alphamath.portfolio.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthFilterContextTest {

  private static final String SECRET = "dev_change_me_please_change_me_32bytes";

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void populatesTrustedAuthenticatedContextFromTokenClaims() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(SECRET);
    String token = token("access", "user-42", "org-1", List.of("USER"), List.of("MEMBER"));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/org/summary");
    request.addHeader("Authorization", "Bearer " + token);
    request.addHeader("X-Org-Roles", "OWNER");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(authentication);
    assertEquals("user-42", authentication.getName());
    assertInstanceOf(AuthenticatedRequestContext.class, authentication.getDetails());
    AuthenticatedRequestContext context = (AuthenticatedRequestContext) authentication.getDetails();
    org.junit.jupiter.api.Assertions.assertTrue(context.mfaVerified());
    assertEquals("org-1", context.orgId());
    assertEquals(Set.of("USER"), context.roles());
    assertEquals(Set.of("MEMBER"), context.orgRoles());
  }

  @Test
  void rejectsNonAccessTokens() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(SECRET);
    String token = token("mfa_challenge", "user-42", "org-1", List.of("USER"), List.of("OWNER"));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/org/summary");
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(401, response.getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  private String token(String type,
                       String uid,
                       String orgId,
                       List<String> roles,
                       List<String> orgRoles) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(600);
    return Jwts.builder()
        .claim("mfa", true)
        .claim("type", type)
        .claim("uid", uid)
        .claim("org_id", orgId)
        .claim("roles", roles)
        .claim("org_roles", orgRoles)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }
}

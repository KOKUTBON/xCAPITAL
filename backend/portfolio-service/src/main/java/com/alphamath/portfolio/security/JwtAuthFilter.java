package com.alphamath.portfolio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final SecretKey key;

  public JwtAuthFilter(@Value("${alphamath.jwt.secret}") String secret) {
    String padded = secret;
    while (padded.getBytes(StandardCharsets.UTF_8).length < 32) padded += "_";
    this.key = Keys.hmacShaKeyFor(padded.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws IOException, jakarta.servlet.ServletException {

    String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (auth == null || !auth.startsWith("Bearer ")) {
      SecurityContextHolder.clearContext();
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    try {
      String token = auth.substring("Bearer ".length()).trim();
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      Object tokenType = claims.get("type");
      if (!"access".equals(tokenType == null ? null : tokenType.toString())) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return;
      }

      String userId = claimString(claims.get("uid"));
      if (userId == null || userId.isBlank()) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return;
      }
      String email = claimString(claims.get("email"));
      String orgId = claimString(claims.get("org_id"));
      Set<String> roles = parseRoles(claims.get("roles"));
      Set<String> orgRoles = parseRoles(claims.get("org_roles"));
      AuthenticatedRequestContext context = new AuthenticatedRequestContext(userId, email, orgId, roles, orgRoles,
          Boolean.TRUE.equals(claims.get("mfa")));
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
      authentication.setDetails(context);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (Exception e) {
      SecurityContextHolder.clearContext();
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String claimString(Object value) {
    if (value == null) {
      return null;
    }
    String normalized = String.valueOf(value).trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private Set<String> parseRoles(Object claim) {
    if (claim == null) {
      return Set.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (claim instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        addRoleValues(out, item == null ? null : String.valueOf(item));
      }
    } else {
      addRoleValues(out, String.valueOf(claim));
    }
    if (out.isEmpty()) {
      return Set.of();
    }
    return Collections.unmodifiableSet(out);
  }

  private void addRoleValues(LinkedHashSet<String> out, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    for (String role : value.split(",")) {
      if (role == null) {
        continue;
      }
      String normalized = role.trim().toUpperCase(Locale.US);
      if (!normalized.isEmpty()) {
        out.add(normalized);
      }
    }
  }
}

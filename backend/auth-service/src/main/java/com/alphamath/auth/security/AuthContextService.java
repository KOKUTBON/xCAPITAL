package com.alphamath.auth.security;

import com.alphamath.auth.user.UserEntity;
import com.alphamath.auth.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AuthContextService {
  private final JwtService jwt;
  private final UserRepository users;

  public AuthContextService(JwtService jwt, UserRepository users) {
    this.jwt = jwt;
    this.users = users;
  }

  public UserEntity requireUser(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
    }
    String token = authHeader.substring("Bearer ".length()).trim();
    try {
      var claims = jwt.parseToken(token);
      if (jwt.isMfaChallenge(claims)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
      }
      Object uidClaim = claims.get("uid");
      String uid = uidClaim == null ? "" : String.valueOf(uidClaim);
      Long id = Long.parseLong(uid);
      UserEntity user = users.findById(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
      if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled");
      }
      return user;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
  }

  public void requireAdmin(String authHeader) {
    UserEntity user = requireUser(authHeader);
    if (!hasRole(user.getRoles(), "ADMIN")) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  public List<String> parseRoles(String roles) {
    if (roles == null || roles.isBlank()) {
      return List.of("USER");
    }
    return java.util.Arrays.stream(roles.split(","))
        .map(String::trim)
        .filter(r -> !r.isEmpty())
        .map(r -> r.toUpperCase(Locale.US))
        .toList();
  }

  public boolean hasRole(String roles, String required) {
    if (required == null || required.isBlank()) {
      return true;
    }
    String needle = required.trim().toUpperCase(Locale.US);
    return parseRoles(roles).stream().anyMatch(r -> r.equals(needle));
  }

  public String normalizeRoles(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return "USER";
    }
    List<String> normalized = roles.stream()
        .filter(r -> r != null && !r.isBlank())
        .map(r -> r.trim().toUpperCase(Locale.US))
        .distinct()
        .toList();
    boolean hasUser = normalized.stream().anyMatch(r -> r.equals("USER"));
    String joined;
    if (hasUser) {
      joined = String.join(",", normalized);
    } else {
      List<String> withUser = new java.util.ArrayList<>(normalized);
      withUser.add("USER");
      joined = String.join(",", withUser);
    }
    return joined.isBlank() ? "USER" : joined;
  }
}

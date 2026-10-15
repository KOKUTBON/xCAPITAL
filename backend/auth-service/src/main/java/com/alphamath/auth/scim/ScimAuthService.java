package com.alphamath.auth.scim;

import com.alphamath.auth.org.OrganizationEntity;
import com.alphamath.auth.org.OrganizationRepository;
import com.alphamath.auth.org.ScimConfigEntity;
import com.alphamath.auth.org.ScimConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ScimAuthService {
  private final ScimConfigRepository configs;
  private final OrganizationRepository orgs;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public ScimAuthService(ScimConfigRepository configs, OrganizationRepository orgs) {
    this.configs = configs;
    this.orgs = orgs;
  }

  public ScimPrincipal authenticate(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing SCIM token");
    }
    String token = authHeader.substring("Bearer ".length()).trim();
    if (token.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing SCIM token");
    }
    List<ScimConfigEntity> enabled = configs.findByEnabledTrue();
    for (ScimConfigEntity config : enabled) {
      if (config.getTokenHash() == null || config.getTokenHash().isBlank()) continue;
      if (encoder.matches(token, config.getTokenHash())) {
        OrganizationEntity org = orgs.findById(config.getOrgId()).orElse(null);
        if (org == null) {
          throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Org not found for SCIM token");
        }
        return new ScimPrincipal(org.getId(), org.getSlug());
      }
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SCIM token");
  }

  public record ScimPrincipal(Long orgId, String orgSlug) {}
}

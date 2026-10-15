package com.alphamath.auth.org;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ProvisioningService {
  private final IdentityProviderRepository identityProviders;
  private final ScimConfigRepository scimConfigs;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public ProvisioningService(IdentityProviderRepository identityProviders, ScimConfigRepository scimConfigs) {
    this.identityProviders = identityProviders;
    this.scimConfigs = scimConfigs;
  }

  public List<IdentityProviderEntity> listProviders(Long orgId) {
    return identityProviders.findByOrgId(orgId);
  }

  @Transactional
  public IdentityProviderEntity upsertProvider(Long orgId, IdentityProviderEntity input) {
    String type = normalizeProviderType(input.getProviderType());
    IdentityProviderEntity existing = identityProviders.findByOrgIdAndProviderType(orgId, type).orElse(null);
    IdentityProviderEntity target = existing == null ? new IdentityProviderEntity() : existing;
    target.setOrgId(orgId);
    target.setProviderType(type);
    target.setIssuer(trimToNull(input.getIssuer()));
    target.setSsoUrl(trimToNull(input.getSsoUrl()));
    target.setMetadataUrl(trimToNull(input.getMetadataUrl()));
    target.setAuthorizationUrl(trimToNull(input.getAuthorizationUrl()));
    target.setTokenUrl(trimToNull(input.getTokenUrl()));
    target.setJwksUrl(trimToNull(input.getJwksUrl()));
    target.setScopes(trimToNull(input.getScopes()));
    target.setRedirectUrl(trimToNull(input.getRedirectUrl()));
    target.setClientId(trimToNull(input.getClientId()));
    target.setClientSecret(trimToNull(input.getClientSecret()));
    target.setX509Cert(trimToNull(input.getX509Cert()));
    target.setEnabled(input.isEnabled());
    target.setUpdatedAt(Instant.now());
    return identityProviders.save(target);
  }

  @Transactional
  public void deleteProvider(Long orgId, Long providerId) {
    IdentityProviderEntity provider = identityProviders.findById(providerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
    if (!provider.getOrgId().equals(orgId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider does not belong to org");
    }
    identityProviders.delete(provider);
  }

  public ScimConfigEntity getScimConfig(Long orgId) {
    return scimConfigs.findByOrgId(orgId).orElse(null);
  }

  @Transactional
  public ScimToken rotateScimToken(Long orgId, String baseUrl, boolean enabled) {
    ScimConfigEntity config = scimConfigs.findByOrgId(orgId).orElseGet(() -> {
      ScimConfigEntity created = new ScimConfigEntity();
      created.setOrgId(orgId);
      return created;
    });
    if (baseUrl != null && !baseUrl.isBlank()) {
      config.setBaseUrl(baseUrl.trim());
    }
    config.setEnabled(enabled);
    String token = generateToken();
    config.setTokenHash(encoder.encode(token));
    config.setLastRotatedAt(Instant.now());
    config.setUpdatedAt(Instant.now());
    scimConfigs.save(config);
    return new ScimToken(token, config.getLastRotatedAt());
  }

  @Transactional
  public ScimConfigEntity updateScimConfig(Long orgId, String baseUrl, boolean enabled) {
    ScimConfigEntity config = scimConfigs.findByOrgId(orgId).orElseGet(() -> {
      ScimConfigEntity created = new ScimConfigEntity();
      created.setOrgId(orgId);
      return created;
    });
    config.setBaseUrl(trimToNull(baseUrl));
    config.setEnabled(enabled);
    config.setUpdatedAt(Instant.now());
    return scimConfigs.save(config);
  }

  private String normalizeProviderType(String type) {
    if (type == null || type.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider type required");
    }
    String normalized = type.trim().toUpperCase(Locale.US);
    return switch (normalized) {
      case "SAML", "OIDC" -> normalized;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider type");
    };
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  private String generateToken() {
    return java.util.UUID.randomUUID().toString().replace("-", "");
  }

  public record ScimToken(String token, Instant rotatedAt) {}
}

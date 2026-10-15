package com.alphamath.auth.org;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "organization_identity_provider")
@Data
public class IdentityProviderEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "org_id", nullable = false)
  private Long orgId;

  @Column(name = "provider_type", nullable = false, length = 32)
  private String providerType;

  @Column(length = 256)
  private String issuer;

  @Column(name = "sso_url", length = 256)
  private String ssoUrl;

  @Column(name = "metadata_url", length = 256)
  private String metadataUrl;

  @Column(name = "authorization_url", length = 256)
  private String authorizationUrl;

  @Column(name = "token_url", length = 256)
  private String tokenUrl;

  @Column(name = "jwks_url", length = 256)
  private String jwksUrl;

  @Column(name = "scopes", length = 256)
  private String scopes;

  @Column(name = "redirect_url", length = 256)
  private String redirectUrl;

  @Column(name = "client_id", length = 128)
  private String clientId;

  @Column(name = "client_secret", length = 256)
  private String clientSecret;

  @Column(name = "x509_cert")
  private String x509Cert;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;
}

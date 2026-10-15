package com.alphamath.auth.sso;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "sso_login_session")
@Data
public class SsoLoginSessionEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "org_id", nullable = false)
  private Long orgId;

  @Column(name = "provider_id", nullable = false)
  private Long providerId;

  @Column(name = "flow_type", nullable = false, length = 16)
  private String flowType;

  @Column(nullable = false, length = 128)
  private String state;

  @Column(length = 128)
  private String nonce;

  @Column(name = "code_verifier", length = 256)
  private String codeVerifier;

  @Column(name = "request_id", length = 128)
  private String requestId;

  @Column(name = "redirect_uri", length = 256)
  private String redirectUri;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "expires_at")
  private Instant expiresAt;
}

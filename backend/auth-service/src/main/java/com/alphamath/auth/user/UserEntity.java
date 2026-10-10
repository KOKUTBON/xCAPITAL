package com.alphamath.auth.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "roles", length = 256)
  private String roles = "USER";

  @Column(name = "primary_org_id")
  private Long primaryOrgId;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "status", nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(name = "disabled_at")
  private Instant disabledAt;

  @Column(name = "mfa_enabled", nullable = false)
  private boolean mfaEnabled = false;

  @Column(name = "mfa_method", length = 32)
  private String mfaMethod;

  @Column(name = "mfa_secret", length = 128)
  private String mfaSecret;

  @Column(name = "mfa_enrolled_at")
  private Instant mfaEnrolledAt;

  @Column(name = "mfa_verified_at")
  private Instant mfaVerifiedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}

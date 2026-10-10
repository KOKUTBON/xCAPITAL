package com.alphamath.auth.org;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "organization_invites")
@Data
public class OrganizationInviteEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "org_id", nullable = false)
  private Long orgId;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(nullable = false, length = 32)
  private String role = "MEMBER";

  @Column(nullable = false, length = 64)
  private String token;

  @Column(nullable = false, length = 32)
  private String status = "PENDING";

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "accepted_at")
  private Instant acceptedAt;
}

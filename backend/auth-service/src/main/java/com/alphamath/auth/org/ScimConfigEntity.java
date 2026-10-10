package com.alphamath.auth.org;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "organization_scim_config")
@Data
public class ScimConfigEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "org_id", nullable = false)
  private Long orgId;

  @Column(name = "base_url", length = 256)
  private String baseUrl;

  @Column(name = "token_hash", length = 255)
  private String tokenHash;

  @Column(nullable = false)
  private boolean enabled = false;

  @Column(name = "last_rotated_at")
  private Instant lastRotatedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;
}

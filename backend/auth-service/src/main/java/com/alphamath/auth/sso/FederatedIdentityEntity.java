package com.alphamath.auth.sso;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "federated_identity")
@Data
public class FederatedIdentityEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "org_id", nullable = false)
  private Long orgId;

  @Column(name = "provider_id", nullable = false)
  private Long providerId;

  @Column(name = "external_subject", nullable = false, length = 256)
  private String externalSubject;

  @Column(length = 320)
  private String email;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;
}

package com.alphamath.auth.sso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface SsoLoginSessionRepository extends JpaRepository<SsoLoginSessionEntity, String> {
  Optional<SsoLoginSessionEntity> findByState(String state);
  void deleteByExpiresAtBefore(Instant cutoff);
}

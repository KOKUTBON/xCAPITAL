package com.alphamath.auth.sso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentityEntity, Long> {
  Optional<FederatedIdentityEntity> findByProviderIdAndExternalSubject(Long providerId, String externalSubject);
  Optional<FederatedIdentityEntity> findByOrgIdAndUserId(Long orgId, Long userId);
}

package com.alphamath.auth.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityProviderRepository extends JpaRepository<IdentityProviderEntity, Long> {
  List<IdentityProviderEntity> findByOrgId(Long orgId);
  Optional<IdentityProviderEntity> findByOrgIdAndProviderType(Long orgId, String providerType);
  List<IdentityProviderEntity> findByOrgIdAndProviderTypeAndEnabledTrue(Long orgId, String providerType);
}

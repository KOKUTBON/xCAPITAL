package com.alphamath.auth.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInviteEntity, Long> {
  List<OrganizationInviteEntity> findByOrgId(Long orgId);
  Optional<OrganizationInviteEntity> findByToken(String token);
  Optional<OrganizationInviteEntity> findByOrgIdAndEmail(Long orgId, String email);
}

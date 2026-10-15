package com.alphamath.auth.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMemberEntity, Long> {
  List<OrganizationMemberEntity> findByUserId(Long userId);
  List<OrganizationMemberEntity> findByOrgId(Long orgId);
  Optional<OrganizationMemberEntity> findByOrgIdAndUserId(Long orgId, Long userId);
  boolean existsByOrgIdAndUserId(Long orgId, Long userId);
}

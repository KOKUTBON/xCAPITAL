package com.alphamath.auth.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScimConfigRepository extends JpaRepository<ScimConfigEntity, Long> {
  Optional<ScimConfigEntity> findByOrgId(Long orgId);
  List<ScimConfigEntity> findByEnabledTrue();
}

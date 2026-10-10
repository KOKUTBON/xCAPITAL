package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceProfileRepository extends JpaRepository<ComplianceProfileEntity, String> {
  long countByOrgId(String orgId);
}

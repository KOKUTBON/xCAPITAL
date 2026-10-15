package com.alphamath.auth.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, Long> {
  Optional<OrganizationEntity> findBySlug(String slug);
  boolean existsBySlug(String slug);
}

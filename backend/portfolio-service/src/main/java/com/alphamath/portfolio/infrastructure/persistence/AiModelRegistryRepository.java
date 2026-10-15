package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiModelRegistryRepository extends JpaRepository<AiModelRegistryEntity, String> {
  List<AiModelRegistryEntity> findByModelNameOrderByCreatedAtDesc(String modelName, Pageable page);
  List<AiModelRegistryEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable page);
}

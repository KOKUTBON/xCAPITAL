package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchCoverageRepository extends JpaRepository<ResearchCoverageEntity, String> {
  List<ResearchCoverageEntity> findBySymbolOrderByPublishedAtDesc(String symbol, PageRequest page);
  List<ResearchCoverageEntity> findByFocusListTrueOrderByPublishedAtDesc(PageRequest page);
  List<ResearchCoverageEntity> findByRatingOrderByPublishedAtDesc(String rating, PageRequest page);
}

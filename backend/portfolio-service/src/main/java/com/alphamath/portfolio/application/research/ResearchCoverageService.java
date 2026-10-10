package com.alphamath.portfolio.application.research;

import com.alphamath.portfolio.domain.research.ResearchCoverage;
import com.alphamath.portfolio.domain.research.ResearchRating;
import com.alphamath.portfolio.infrastructure.persistence.ResearchCoverageEntity;
import com.alphamath.portfolio.infrastructure.persistence.ResearchCoverageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ResearchCoverageService {
  private final ResearchCoverageRepository coverage;

  public ResearchCoverageService(ResearchCoverageRepository coverage) {
    this.coverage = coverage;
  }

  public List<ResearchCoverage> list(String symbol, String rating, Boolean focusList, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    List<ResearchCoverageEntity> rows;

    if (symbol != null && !symbol.isBlank()) {
      rows = coverage.findBySymbolOrderByPublishedAtDesc(symbol.trim().toUpperCase(Locale.US), PageRequest.of(0, size));
    } else if (Boolean.TRUE.equals(focusList)) {
      rows = coverage.findByFocusListTrueOrderByPublishedAtDesc(PageRequest.of(0, size));
    } else if (rating != null && !rating.isBlank()) {
      ResearchRating parsed = parseRating(rating);
      rows = coverage.findByRatingOrderByPublishedAtDesc(parsed.name(), PageRequest.of(0, size));
    } else {
      rows = coverage.findAll(PageRequest.of(0, size)).getContent();
    }

    List<ResearchCoverage> out = new ArrayList<>();
    for (ResearchCoverageEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  public List<ResearchCoverage> focusList(int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    List<ResearchCoverage> out = new ArrayList<>();
    for (ResearchCoverageEntity entity : coverage.findByFocusListTrueOrderByPublishedAtDesc(PageRequest.of(0, size))) {
      out.add(toDto(entity));
    }
    return out;
  }

  private ResearchRating parseRating(String raw) {
    try {
      return ResearchRating.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rating");
    }
  }

  private ResearchCoverage toDto(ResearchCoverageEntity entity) {
    ResearchCoverage out = new ResearchCoverage();
    out.setId(entity.getId());
    out.setSymbol(entity.getSymbol());
    out.setRating(ResearchRating.valueOf(entity.getRating()));
    out.setPriceTarget(entity.getPriceTarget());
    out.setFocusList(entity.isFocusList());
    out.setAnalyst(entity.getAnalyst());
    out.setSummary(entity.getSummary());
    out.setSource(entity.getSource());
    out.setPublishedAt(entity.getPublishedAt());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }
}

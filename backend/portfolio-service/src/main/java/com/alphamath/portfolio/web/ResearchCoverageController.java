package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.research.ResearchCoverageService;
import com.alphamath.portfolio.domain.research.ResearchCoverage;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchCoverageController {
  private final ResearchCoverageService coverage;

  public ResearchCoverageController(ResearchCoverageService coverage) {
    this.coverage = coverage;
  }

  @GetMapping("/coverage")
  public List<ResearchCoverage> list(@RequestParam(required = false) String symbol,
                                     @RequestParam(required = false) String rating,
                                     @RequestParam(required = false) Boolean focusList,
                                     @RequestParam(required = false) Integer limit) {
    return coverage.list(symbol, rating, focusList, limit == null ? 50 : limit);
  }

  @GetMapping("/focus-list")
  public List<ResearchCoverage> focusList(@RequestParam(required = false) Integer limit) {
    return coverage.focusList(limit == null ? 50 : limit);
  }
}

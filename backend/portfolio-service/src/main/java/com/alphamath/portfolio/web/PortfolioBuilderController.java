package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.portfolio.PortfolioBuilderService;
import com.alphamath.portfolio.domain.portfolio.PortfolioBuilderRequest;
import com.alphamath.portfolio.domain.portfolio.PortfolioBuilderResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolio/builder")
public class PortfolioBuilderController {
  private final PortfolioBuilderService builder;

  public PortfolioBuilderController(PortfolioBuilderService builder) {
    this.builder = builder;
  }

  @PostMapping("/analyze")
  public PortfolioBuilderResult analyze(@RequestBody PortfolioBuilderRequest req) {
    return builder.analyze(req);
  }
}

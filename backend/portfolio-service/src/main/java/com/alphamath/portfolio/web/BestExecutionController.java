package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.execution.BestExecutionService;
import com.alphamath.portfolio.domain.execution.BestExecutionRecord;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/best-execution")
public class BestExecutionController {
  private final BestExecutionService bestExecution;

  public BestExecutionController(BestExecutionService bestExecution) {
    this.bestExecution = bestExecution;
  }

  @GetMapping
  public List<BestExecutionRecord> list(@RequestParam(required = false) String symbol,
                                        @RequestParam(required = false) Integer limit,
                                        Principal principal) {
    return bestExecution.list(userId(principal), symbol, limit == null ? 50 : limit);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

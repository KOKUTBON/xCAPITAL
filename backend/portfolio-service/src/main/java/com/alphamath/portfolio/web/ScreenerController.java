package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.screener.ScreenerService;
import com.alphamath.portfolio.domain.screener.ScreenerQueryRequest;
import com.alphamath.portfolio.domain.screener.ScreenerResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/screeners")
public class ScreenerController {
  private final ScreenerService screeners;

  public ScreenerController(ScreenerService screeners) {
    this.screeners = screeners;
  }

  @PostMapping("/query")
  public ScreenerResult query(@RequestBody(required = false) ScreenerQueryRequest req) {
    return screeners.query(req);
  }
}

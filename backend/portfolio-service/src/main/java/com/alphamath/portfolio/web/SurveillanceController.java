package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.surveillance.SurveillanceService;
import com.alphamath.portfolio.domain.surveillance.SurveillanceAlert;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/surveillance")
public class SurveillanceController {
  private final SurveillanceService surveillance;

  public SurveillanceController(SurveillanceService surveillance) {
    this.surveillance = surveillance;
  }

  @GetMapping("/alerts")
  public List<SurveillanceAlert> alerts(@RequestParam(required = false) Integer limit, Principal principal) {
    return surveillance.list(userId(principal), limit == null ? 50 : limit);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

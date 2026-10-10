package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.wealth.WealthPlanService;
import com.alphamath.portfolio.domain.wealth.WealthPlan;
import com.alphamath.portfolio.domain.wealth.WealthPlanRequest;
import com.alphamath.portfolio.domain.wealth.WealthPlanSimulationRequest;
import com.alphamath.portfolio.domain.wealth.WealthPlanSimulationResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wealth/plan")
public class WealthPlanController {
  private final WealthPlanService plans;

  public WealthPlanController(WealthPlanService plans) {
    this.plans = plans;
  }

  @PostMapping
  public WealthPlan create(@RequestBody WealthPlanRequest req, Principal principal) {
    return plans.create(userId(principal), req);
  }

  @GetMapping
  public List<WealthPlan> list(Principal principal) {
    return plans.list(userId(principal));
  }

  @GetMapping("/{id}")
  public WealthPlan get(@PathVariable String id, Principal principal) {
    return plans.get(userId(principal), id);
  }

  @PostMapping("/{id}/simulate")
  public WealthPlanSimulationResult simulate(@PathVariable String id,
                                             @RequestBody(required = false) WealthPlanSimulationRequest req,
                                             Principal principal) {
    return plans.simulate(userId(principal), id, req);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

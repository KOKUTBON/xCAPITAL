package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.autoinvest.AutoInvestService;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestFee;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestPlan;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestPlanRequest;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestPlanStatus;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestRun;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auto-invest")
public class AutoInvestController {
  private final AutoInvestService autoInvest;

  public AutoInvestController(AutoInvestService autoInvest) {
    this.autoInvest = autoInvest;
  }

  @PostMapping("/plans")
  public AutoInvestPlan create(@Valid @RequestBody AutoInvestPlanRequest req, Principal principal) {
    return autoInvest.createPlan(userId(principal), req);
  }

  @GetMapping("/plans")
  public List<AutoInvestPlan> list(Principal principal) {
    return autoInvest.listPlans(userId(principal));
  }

  @GetMapping("/model-portfolios")
  public List<AutoInvestModelPortfolio> modelPortfolios() {
    return autoInvest.modelPortfolios();
  }

  @GetMapping("/plans/{id}")
  public AutoInvestPlan plan(@PathVariable String id, Principal principal) {
    return autoInvest.getPlan(userId(principal), id);
  }

  @PostMapping("/plans/{id}/status")
  public AutoInvestPlan updateStatus(@PathVariable String id, @Valid @RequestBody StatusRequest req, Principal principal) {
    AutoInvestPlanStatus status;
    try {
      status = AutoInvestPlanStatus.valueOf(req.status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
    }
    return autoInvest.updateStatus(userId(principal), id, status);
  }

  @PostMapping("/plans/{id}/run")
  public AutoInvestRun run(@PathVariable String id, Principal principal) {
    return autoInvest.runNow(userId(principal), id);
  }

  @GetMapping("/plans/{id}/runs")
  public List<AutoInvestRun> runs(@PathVariable String id, Principal principal) {
    return autoInvest.listRuns(userId(principal), id);
  }

  @GetMapping("/plans/{id}/fees")
  public List<AutoInvestFee> fees(@PathVariable String id, Principal principal) {
    return autoInvest.listFees(userId(principal), id);
  }

  @PostMapping("/plans/{id}/fees/charge")
  public AutoInvestFee charge(@PathVariable String id, Principal principal) {
    return autoInvest.chargeFeeNow(userId(principal), id);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  @Data
  public static class StatusRequest {
    @jakarta.validation.constraints.NotNull
    public String status;
  }
}

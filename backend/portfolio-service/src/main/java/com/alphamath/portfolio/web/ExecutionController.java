package com.alphamath.portfolio.web;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.execution.BrokerLinkRequest;
import com.alphamath.portfolio.domain.execution.BrokerProviderInfo;
import com.alphamath.portfolio.domain.execution.ExecutionIntent;
import com.alphamath.portfolio.application.execution.ExecutionService;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.trade.TradeProposal;
import com.alphamath.portfolio.application.trade.TradeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/execution")
public class ExecutionController {
  private final ExecutionService execution;
  private final TradeService trade;
  private final SecurityGuard security;

  public ExecutionController(ExecutionService execution, TradeService trade, SecurityGuard security) {
    this.execution = execution;
    this.trade = trade;
    this.security = security;
  }

  @GetMapping("/providers")
  public List<BrokerProviderInfo> providers(@RequestParam(required = false) Region region,
                                            @RequestParam(required = false) AssetClass assetClass) {
    return execution.listProviders(region, assetClass);
  }

  @GetMapping("/accounts")
  public List<BrokerAccount> accounts(Principal principal) {
    return execution.listAccounts(userId(principal));
  }

  @PostMapping("/accounts/link")
  public BrokerAccount link(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                            @Valid @RequestBody BrokerLinkRequest req,
                            Principal principal) {
    security.requireMfa(mfa, "execution account link");
    return execution.linkAccount(userId(principal), req);
  }

  @GetMapping("/intents")
  public List<ExecutionIntent> intents(Principal principal) {
    return execution.listIntents(userId(principal));
  }

  @GetMapping("/intents/{id}")
  public ExecutionIntent intent(@PathVariable String id, Principal principal) {
    return execution.getIntent(userId(principal), id);
  }

  @PostMapping("/intents/{proposalId}")
  public ExecutionIntent create(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                @PathVariable String proposalId,
                                Principal principal) {
    security.requireMfa(mfa, "execution intent");
    TradeProposal proposal = trade.getProposal(userId(principal), proposalId);
    return execution.createIntent(userId(principal), proposal);
  }

  @PostMapping("/intents/{id}/simulate-fill")
  public ExecutionIntent simulateFill(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                      @PathVariable String id,
                                      Principal principal) {
    security.requireMfa(mfa, "execution simulate fill");
    return execution.simulateFill(userId(principal), id);
  }

  @PostMapping("/intents/{id}/submit")
  public ExecutionIntent submit(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                @PathVariable String id,
                                Principal principal) {
    security.requireMfa(mfa, "execution submit");
    return execution.submitIntent(userId(principal), id);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

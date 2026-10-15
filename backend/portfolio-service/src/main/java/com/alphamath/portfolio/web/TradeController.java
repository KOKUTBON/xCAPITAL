package com.alphamath.portfolio.web;

import com.alphamath.portfolio.domain.trade.PaperAccount;
import com.alphamath.portfolio.domain.trade.TradeDecisionRequest;
import com.alphamath.portfolio.domain.trade.TradeProposal;
import com.alphamath.portfolio.domain.trade.TradeProposalRequest;
import com.alphamath.portfolio.domain.trade.TradeSeedRequest;
import com.alphamath.portfolio.application.trade.TradeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/trade")
public class TradeController {
  private final TradeService trade;
  private final SecurityGuard security;

  public TradeController(TradeService trade, SecurityGuard security) {
    this.trade = trade;
    this.security = security;
  }

  @GetMapping("/account")
  public PaperAccount account(Principal principal) {
    return trade.getAccount(userId(principal));
  }

  @PostMapping("/account/seed")
  public PaperAccount seed(@Valid @RequestBody TradeSeedRequest req, Principal principal) {
    return trade.seedAccount(userId(principal), req);
  }

  @PostMapping("/proposals")
  public TradeProposal propose(@Valid @RequestBody TradeProposalRequest req, Principal principal) {
    return trade.createProposal(userId(principal), req);
  }

  @GetMapping("/proposals/{id}")
  public TradeProposal proposal(@PathVariable String id, Principal principal) {
    return trade.getProposal(userId(principal), id);
  }

  @PostMapping("/proposals/{id}/decision")
  public TradeProposal decide(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                              @PathVariable String id,
                              @Valid @RequestBody TradeDecisionRequest req,
                              Principal principal) {
    security.requireMfa(mfa, "trade approval");
    return trade.decide(userId(principal), id, req);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

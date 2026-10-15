package com.alphamath.portfolio.web;

import com.alphamath.portfolio.domain.funding.FundingDepositReceipt;
import com.alphamath.portfolio.domain.funding.FundingDepositRequest;
import com.alphamath.portfolio.domain.funding.FundingLinkRequest;
import com.alphamath.portfolio.domain.funding.FundingProviderInfo;
import com.alphamath.portfolio.application.funding.FundingService;
import com.alphamath.portfolio.domain.funding.FundingSource;
import com.alphamath.portfolio.domain.funding.FundingTransferReceipt;
import com.alphamath.portfolio.domain.funding.FundingTransferRequest;
import com.alphamath.portfolio.domain.funding.FundingVerifyRequest;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalReceipt;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/funding")
public class FundingController {
  private final FundingService funding;
  private final SecurityGuard security;

  public FundingController(FundingService funding, SecurityGuard security) {
    this.funding = funding;
    this.security = security;
  }

  @GetMapping("/providers")
  public List<FundingProviderInfo> providers() {
    return funding.listProviders();
  }

  @GetMapping("/sources")
  public List<FundingSource> sources(Principal principal) {
    return funding.listSources(userId(principal));
  }

  @PostMapping("/sources")
  public FundingSource link(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                            @Valid @RequestBody FundingLinkRequest req,
                            Principal principal) {
    security.requireMfa(mfa, "funding source link");
    return funding.linkSource(userId(principal), req);
  }

  @PostMapping("/sources/{id}/verify")
  public FundingSource verify(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                              @PathVariable String id,
                              @RequestBody FundingVerifyRequest req,
                              Principal principal) {
    security.requireMfa(mfa, "funding source verification");
    return funding.verifySource(userId(principal), id, req);
  }

  @PostMapping("/deposits")
  public FundingDepositReceipt deposit(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                       @Valid @RequestBody FundingDepositRequest req,
                                       Principal principal) {
    security.requireMfa(mfa, "funding deposit");
    return funding.deposit(userId(principal), req);
  }

  @GetMapping("/deposits")
  public List<FundingDepositReceipt> deposits(Principal principal) {
    return funding.listDeposits(userId(principal));
  }

  @PostMapping("/withdrawals")
  public FundingWithdrawalReceipt withdraw(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                           @Valid @RequestBody FundingWithdrawalRequest req,
                                           Principal principal) {
    security.requireMfa(mfa, "funding withdrawal");
    return funding.withdraw(userId(principal), req);
  }

  @GetMapping("/withdrawals")
  public List<FundingWithdrawalReceipt> withdrawals(Principal principal) {
    return funding.listWithdrawals(userId(principal));
  }

  @PostMapping("/transfers")
  public FundingTransferReceipt transfer(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                         @Valid @RequestBody FundingTransferRequest req,
                                         Principal principal) {
    security.requireMfa(mfa, "funding transfer");
    return funding.transfer(userId(principal), req);
  }

  @GetMapping("/transfers")
  public List<FundingTransferReceipt> transfers(Principal principal) {
    return funding.listTransfers(userId(principal));
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

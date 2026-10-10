package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.banking.BankingService;
import com.alphamath.portfolio.domain.banking.BankingAccount;
import com.alphamath.portfolio.domain.banking.BankingTransfer;
import com.alphamath.portfolio.domain.banking.BankingTransferRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/banking")
public class BankingController {
  private final BankingService banking;

  public BankingController(BankingService banking) {
    this.banking = banking;
  }

  @GetMapping("/account")
  public BankingAccount account(Principal principal) {
    return banking.getAccount(userId(principal));
  }

  @PostMapping("/transfer")
  public BankingTransfer transfer(@RequestBody BankingTransferRequest req, Principal principal) {
    return banking.transfer(userId(principal), req);
  }

  @GetMapping("/transfers")
  public List<BankingTransfer> transfers(@RequestParam(required = false) Integer limit, Principal principal) {
    return banking.listTransfers(userId(principal), limit == null ? 50 : limit);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

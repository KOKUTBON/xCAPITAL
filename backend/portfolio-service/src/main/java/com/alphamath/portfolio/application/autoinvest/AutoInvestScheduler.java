package com.alphamath.portfolio.application.autoinvest;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AutoInvestScheduler {
  private final AutoInvestService autoInvest;

  public AutoInvestScheduler(AutoInvestService autoInvest) {
    this.autoInvest = autoInvest;
  }

  @Scheduled(fixedDelayString = "${alphamath.autoinvest.schedulerDelayMs:60000}")
  public void run() {
    autoInvest.runScheduled();
    autoInvest.runAdvisoryFees();
  }
}

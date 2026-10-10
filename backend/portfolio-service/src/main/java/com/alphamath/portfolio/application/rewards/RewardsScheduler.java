package com.alphamath.portfolio.application.rewards;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RewardsScheduler {
  private final RewardsService rewards;

  public RewardsScheduler(RewardsService rewards) {
    this.rewards = rewards;
  }

  @Scheduled(fixedDelayString = "${alphamath.rewards.evalDelayMs:3600000}")
  public void evaluatePending() {
    rewards.evaluatePending();
  }
}

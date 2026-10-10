package com.alphamath.simulation.engine;

import com.alphamath.simulation.model.SimulationRequest;
import com.alphamath.simulation.model.SimulationResult;
import com.alphamath.simulation.model.Strategy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SimulationEngine {

  public SimulationResult run(SimulationRequest req) {
    Strategy strategy = req.getStrategy() == null ? Strategy.BUY_AND_HOLD : req.getStrategy();
    double initialCash = req.getInitialCash() == null ? 10000.0 : req.getInitialCash();
    double contribution = req.getContribution() == null ? 0.0 : req.getContribution();
    int contributionEvery = req.getContributionEvery() == null ? 1 : req.getContributionEvery();

    if (initialCash <= 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "initialCash must be > 0");
    }
    if (contribution < 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contribution must be >= 0");
    }
    if (contributionEvery < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contributionEvery must be >= 1");
    }

    List<Double> returns = req.getReturns();
    double price = 1.0;
    double cash = initialCash;
    double shares = 0.0;
    double totalContributed = initialCash;
    int contributionCount = 0;

    double equity = cash;
    double peak = equity;
    double maxDrawdown = 0.0;
    List<Double> equityCurve = new ArrayList<>();
    List<Double> drawdownCurve = new ArrayList<>();

    for (int i = 0; i < returns.size(); i++) {
      if (strategy == Strategy.DCA && contribution > 0.0 && i % contributionEvery == 0) {
        cash += contribution;
        totalContributed += contribution;
        contributionCount++;
      }

      if (cash > 0.0) {
        shares += cash / price;
        cash = 0.0;
      }

      double r = returns.get(i);
      if (!Double.isFinite(r) || r <= -1.0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "returns must be finite and > -1");
      }
      price *= (1.0 + r);

      equity = shares * price + cash;
      if (equity > peak) {
        peak = equity;
      } else if (peak > 0.0) {
        double dd = (peak - equity) / peak;
        if (dd > maxDrawdown) maxDrawdown = dd;
      }
      double drawdown = peak <= 0.0 ? 0.0 : (peak - equity) / peak;
      equityCurve.add(equity);
      drawdownCurve.add(drawdown);
    }

    double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    double m2 = 0.0;
    for (double x : returns) {
      double d = x - mean;
      m2 += d * d;
    }
    double variance = returns.size() > 1 ? m2 / (returns.size() - 1) : 0.0;
    double stdDev = Math.sqrt(Math.max(0.0, variance));

    double totalReturn = totalContributed <= 0.0 ? 0.0 : (equity - totalContributed) / totalContributed;

    SimulationResult result = new SimulationResult();
    result.setStrategy(strategy);
    result.setPeriods(returns.size());
    result.setInitialCash(initialCash);
    result.setContribution(contribution);
    result.setContributionEvery(contributionEvery);
    result.setContributionCount(contributionCount);
    result.setTotalContributed(totalContributed);
    result.setFinalEquity(equity);
    result.setTotalReturn(totalReturn);
    result.setMaxDrawdown(maxDrawdown);
    result.setMeanReturn(mean);
    result.setStdDev(stdDev);
    result.setEquityCurve(equityCurve);
    result.setDrawdownCurve(drawdownCurve);
    result.setCreatedAt(Instant.now());
    result.setDisclaimer("Educational backtest only. Not financial advice.");
    return result;
  }
}

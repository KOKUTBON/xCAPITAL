package com.alphamath.portfolio.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.alphamath.portfolio.math.Optimizers;
import com.alphamath.portfolio.math.BlackLitterman;
import com.alphamath.portfolio.math.MathUtils;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

  /**
   * IMPORTANT: This endpoint returns an optimization proposal output, not financial advice.
   * For MVP, we use a robust heuristic (random search with constraints).
   * Replace with QP solver in production.
   */
  @PostMapping("/optimize")
  public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest req) {
    int n = req.mu.size();

    // Basic validation for covariance shape
    if (req.cov.size() != n) throw new IllegalArgumentException("cov must be NxN");
    for (var row : req.cov) if (row.size() != n) throw new IllegalArgumentException("cov must be NxN");


    // If a deterministic optimizer is requested, use it; otherwise fall back to random MVP.
    if (req.method != null && req.method != Optimizers.Method.RANDOM_MVP) {
      double[] mu = MathUtils.toVector(req.mu);
      double[][] cov = MathUtils.toMatrix(req.cov);

      double[] w;
      switch (req.method) {
        case MEAN_VARIANCE_PGD -> w = Optimizers.optimizeMeanVariancePGD(mu, cov, req.riskAversion, req.minWeight, req.maxWeight, 4000, 0.02);
        case MIN_VARIANCE -> w = Optimizers.optimizeMinVariance(cov, req.minWeight, req.maxWeight, 4000, 0.02);
        case RISK_PARITY -> w = Optimizers.optimizeRiskParity(cov, req.minWeight, req.maxWeight, 1200);
        case KELLY_APPROX -> w = Optimizers.optimizeKellyApprox(mu, cov, req.fractionalKelly == null ? 0.25 : req.fractionalKelly, req.minWeight, req.maxWeight);
        case BLACK_LITTERMAN_MEANVAR -> {
          // If views not provided, this acts like a mild shrinkage toward prior.
          if (req.P == null || req.P.isEmpty() || req.q == null || req.q.isEmpty() || req.omegaDiag == null || req.omegaDiag.isEmpty()) {
            w = Optimizers.optimizeMeanVariancePGD(mu, cov, req.riskAversion, req.minWeight, req.maxWeight, 4000, 0.02);
          } else {
            double[][] Pm = new double[req.P.size()][mu.length];
            for (int i=0;i<req.P.size();i++){
              var row=req.P.get(i);
              for (int j=0;j<mu.length;j++) Pm[i][j]=row.get(j);
            }
            double[] qv = MathUtils.toVector(req.q);
            double[] od = MathUtils.toVector(req.omegaDiag);
            double tau = req.tau == null ? 0.05 : req.tau;
            var bl = BlackLitterman.posterior(mu, cov, tau, Pm, qv, od);
            w = Optimizers.optimizeMeanVariancePGD(bl.posteriorMu, bl.posteriorCov, req.riskAversion, req.minWeight, req.maxWeight, 5000, 0.02);
          }
        }
        default -> w = Optimizers.optimizeMeanVariancePGD(mu, cov, req.riskAversion, req.minWeight, req.maxWeight, 4000, 0.02);
      }

      double exp = 0.0;
      for (int i = 0; i < mu.length; i++) exp += w[i] * mu[i];
      double var = MathUtils.quadForm(cov, w);

      return new OptimizeResponse(MathUtils.toList(w), exp, var,
          "Optimization output only. Not financial advice.");
    }

    Random rng = new Random(42);
    double bestObj = Double.POSITIVE_INFINITY;
    double[] bestW = null;

    int iters = 20000;
    for (int t = 0; t < iters; t++) {
      double[] w = randomSimplex(n, rng);

      // constraints
      boolean ok = true;
      for (double x : w) {
        if (x > req.maxWeight || x < req.minWeight) { ok = false; break; }
      }
      if (!ok) continue;

      double ret = dot(w, req.mu);
      double var = quad(w, req.cov);

      double obj = req.riskAversion * var - ret;
      if (obj < bestObj) {
        bestObj = obj;
        bestW = w;
      }
    }

    if (bestW == null) {
      bestW = new double[n];
      for (int i = 0; i < n; i++) bestW[i] = 1.0 / n;
    }

    double expRet = dot(bestW, req.mu);
    double variance = quad(bestW, req.cov);

    List<Double> weights = new ArrayList<>();
    for (double x : bestW) weights.add(x);

    return new OptimizeResponse(weights, expRet, variance, "MVP heuristic optimizer. Not financial advice.");
  }

  private static double[] randomSimplex(int n, Random rng) {
    double[] x = new double[n];
    double sum = 0;
    for (int i = 0; i < n; i++) {
      x[i] = rng.nextDouble();
      sum += x[i];
    }
    for (int i = 0; i < n; i++) x[i] /= sum;
    return x;
  }

  private static double dot(double[] w, List<Double> mu) {
    double s = 0;
    for (int i = 0; i < w.length; i++) s += w[i] * mu.get(i);
    return s;
  }

  private static double quad(double[] w, List<List<Double>> cov) {
    double s = 0;
    int n = w.length;
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        s += w[i] * cov.get(i).get(j) * w[j];
      }
    }
    return s;
  }

  @Data
  public static class OptimizeRequest {

    /** Optimizer method */
    public Optimizers.Method method = Optimizers.Method.RANDOM_MVP;

    /** For Kelly */
    public Double fractionalKelly = 0.25;

    /** For Black–Litterman */
    public Double tau = 0.05;
    public java.util.List<java.util.List<Double>> P = new java.util.ArrayList<>();
    public java.util.List<Double> q = new java.util.ArrayList<>();
    public java.util.List<Double> omegaDiag = new java.util.ArrayList<>();

    @NotNull @Size(min = 2, max = 50)
    public List<@NotNull Double> mu;

    @NotNull @Size(min = 2, max = 50)
    public List<List<@NotNull Double>> cov;

    @NotNull @Min(1) @Max(100)
    public Integer riskAversion = 6;

    @NotNull
    public Double maxWeight = 0.6;

    @NotNull
    public Double minWeight = 0.0;
  }

  @Data
  public static class OptimizeResponse {
    public final List<Double> weights;
    public final double expectedReturn;
    public final double variance;
    public final String disclaimer;
  }
}

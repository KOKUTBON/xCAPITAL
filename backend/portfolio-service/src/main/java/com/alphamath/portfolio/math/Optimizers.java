package com.alphamath.portfolio.math;

public final class Optimizers {
  private Optimizers(){}

  public enum Method {
    RANDOM_MVP,
    MEAN_VARIANCE_PGD,
    MIN_VARIANCE,
    RISK_PARITY,
    KELLY_APPROX,
    BLACK_LITTERMAN_MEANVAR
  }

  public static double[] optimizeMeanVariancePGD(double[] mu, double[][] cov, double riskAversion,
      double minW, double maxW, int steps, double lr) {

    int n = mu.length;
    double[] w = MathUtils.equalWeights(n);
    for (int t=0;t<steps;t++) {
      // grad of objective: f = -mu^T w + riskAversion * w^T cov w
      // grad = -mu + 2*riskAversion*cov*w
      double[] covw = MathUtils.matVec(cov, w);
      double[] grad = new double[n];
      for (int i=0;i<n;i++) grad[i] = -mu[i] + 2.0 * riskAversion * covw[i];

      // gradient step
      for (int i=0;i<n;i++) w[i] -= lr * grad[i];

      // project to constraints
      w = MathUtils.projectToBoxedSimplex(w, minW, maxW, 50);
    }
    return w;
  }

  /** Minimum variance portfolio via projected gradient descent (mu = 0). */
  public static double[] optimizeMinVariance(double[][] cov, double minW, double maxW, int steps, double lr) {
    double[] muZero = new double[cov.length];
    return optimizeMeanVariancePGD(muZero, cov, 1.0, minW, maxW, steps, lr);
  }

  /**
   * Risk parity weights (equal risk contribution) using iterative scaling.
   * Uses covariance matrix cov and assumes long-only weights.
   */
  public static double[] optimizeRiskParity(double[][] cov, double minW, double maxW, int iters) {
    int n = cov.length;
    double[] w = MathUtils.equalWeights(n);

    for (int t=0;t<iters;t++) {
      double[] covw = MathUtils.matVec(cov, w);
      double portVar = MathUtils.dot(w, covw);
      double portVol = Math.sqrt(Math.max(1e-18, portVar));
      double targetRC = portVol / n;

      // risk contribution RC_i = w_i * (cov*w)_i / portVol
      // Update: w_i <- w_i * targetRC / RC_i
      for (int i=0;i<n;i++) {
        double rc = (w[i] * covw[i]) / Math.max(1e-18, portVol);
        double mult = targetRC / Math.max(1e-18, rc);
        w[i] *= mult;
      }

      w = MathUtils.projectToBoxedSimplex(w, minW, maxW, 50);
    }
    return w;
  }

  /**
   * Multi-asset Kelly (quadratic approximation): maximize mu^T w - 0.5 w^T cov w
   * Unconstrained solution is w = cov^{-1} mu. We then project to constraints and allow fractionalKelly.
   */
  public static double[] optimizeKellyApprox(double[] mu, double[][] cov, double fractionalKelly,
      double minW, double maxW) {
    double[] w;
    try {
      w = LinearAlgebra.solve(cov, mu); // cov * w = mu
    } catch (Exception e) {
      w = MathUtils.equalWeights(mu.length);
    }

    // fractional Kelly shrink
    for (int i=0;i<w.length;i++) w[i] *= fractionalKelly;

    // project to simplex/box; if projection distorts too much, still yields feasible weights
    w = MathUtils.projectToBoxedSimplex(w, minW, maxW, 80);
    return w;
  }
}

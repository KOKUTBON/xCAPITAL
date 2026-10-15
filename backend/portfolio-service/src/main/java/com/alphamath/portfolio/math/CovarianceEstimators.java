package com.alphamath.portfolio.math;

import java.util.List;

public final class CovarianceEstimators {
  private CovarianceEstimators(){}

  /** Sample covariance for returns matrix: T x N */
  public static double[][] sampleCov(double[][] returns) {
    int T = returns.length;
    int N = returns[0].length;

    double[] mean = new double[N];
    for (int t=0;t<T;t++) for(int i=0;i<N;i++) mean[i] += returns[t][i];
    for (int i=0;i<N;i++) mean[i] /= T;

    double[][] cov = new double[N][N];
    for (int t=0;t<T;t++) {
      for (int i=0;i<N;i++) {
        double di = returns[t][i] - mean[i];
        for (int j=0;j<N;j++) {
          double dj = returns[t][j] - mean[j];
          cov[i][j] += di*dj;
        }
      }
    }
    double denom = Math.max(1, T-1);
    for (int i=0;i<N;i++) for(int j=0;j<N;j++) cov[i][j] /= denom;
    return cov;
  }

  /** EWMA covariance (RiskMetrics-style). lambda ~ 0.94 for daily. */
  public static double[][] ewmaCov(double[][] returns, double lambda) {
    int T = returns.length;
    int N = returns[0].length;
    double[][] cov = new double[N][N];
    for (int t=0;t<T;t++) {
      double[] r = returns[t];
      for (int i=0;i<N;i++) {
        for (int j=0;j<N;j++) {
          cov[i][j] = lambda * cov[i][j] + (1.0 - lambda) * (r[i]*r[j]);
        }
      }
    }
    return cov;
  }

  /**
   * Simple shrinkage toward diagonal (a practical "big leagues" default).
   * alpha in [0,1] where 0=sample cov, 1=diagonal only.
   * (Ledoit-Wolf is more precise; this is robust & dependency-free.)
   */
  public static double[][] shrinkToDiagonal(double[][] sampleCov, double alpha) {
    int N = sampleCov.length;
    double[][] out = new double[N][N];
    for (int i=0;i<N;i++) {
      for (int j=0;j<N;j++) {
        double target = (i==j) ? sampleCov[i][i] : 0.0;
        out[i][j] = (1.0-alpha) * sampleCov[i][j] + alpha * target;
      }
    }
    return out;
  }
}

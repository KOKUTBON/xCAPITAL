package com.alphamath.portfolio.math;

public final class BlackLitterman {
  private BlackLitterman(){}

  public static class Result {
    public final double[] posteriorMu;
    public final double[][] posteriorCov;
    public Result(double[] mu, double[][] cov) { this.posteriorMu = mu; this.posteriorCov = cov; }
  }

  /**
   * Black–Litterman posterior:
   * mu = inv(inv(tau*Sigma) + P^T inv(Omega) P) * (inv(tau*Sigma) * pi + P^T inv(Omega) q)
   *
   * Where:
   * - Sigma is prior covariance
   * - pi is prior mean (or implied equilibrium returns)
   * - P is KxN view matrix
   * - q is K vector of view returns
   * - Omega is KxK (typically diagonal uncertainties)
   */
  public static Result posterior(double[] pi, double[][] Sigma, double tau,
      double[][] P, double[] q, double[] omegaDiag) {

    int n = pi.length;
    int k = q.length;

    double[][] tauSigma = new double[n][n];
    for (int i=0;i<n;i++) for(int j=0;j<n;j++) tauSigma[i][j] = tau * Sigma[i][j];

    // A = inv(tauSigma)
    // We'll avoid explicit inversion by solving linear systems using LinearAlgebra with identity columns.
    double[][] A = invert(tauSigma);

    // Compute PT_Oinv_P = P^T * inv(Omega) * P
    double[][] Oinv = new double[k][k];
    for (int i=0;i<k;i++) Oinv[i][i] = 1.0 / Math.max(1e-18, omegaDiag[i]);

    double[][] OinvP = LinearAlgebra.matMul(Oinv, P);         // KxN
    double[][] PT = LinearAlgebra.transpose(P);               // NxK
    double[][] PT_Oinv_P = LinearAlgebra.matMul(PT, OinvP);   // NxN

    double[][] M = LinearAlgebra.add(A, PT_Oinv_P, 1.0);

    // rhs = A*pi + P^T * inv(Omega) * q
    double[] Api = LinearAlgebra.matVec(A, pi);
    double[] Oinvq = new double[k];
    for (int i=0;i<k;i++) Oinvq[i] = Oinv[i][i] * q[i];
    double[] PT_Oinv_q = LinearAlgebra.matVec(PT, Oinvq);

    double[] rhs = new double[n];
    for (int i=0;i<n;i++) rhs[i] = Api[i] + PT_Oinv_q[i];

    double[] muPost = LinearAlgebra.solve(M, rhs);

    // posterior covariance: Sigma + inv(M)   (common approximation)
    double[][] Minv = invert(M);
    double[][] covPost = LinearAlgebra.add(Sigma, Minv, 1.0);

    return new Result(muPost, covPost);
  }

  private static double[][] invert(double[][] A) {
    int n = A.length;
    double[][] inv = new double[n][n];
    for (int col=0; col<n; col++) {
      double[] e = new double[n];
      e[col] = 1.0;
      double[] x = LinearAlgebra.solve(A, e);
      for (int row=0; row<n; row++) inv[row][col] = x[row];
    }
    return inv;
  }
}

package com.alphamath.portfolio.math;

public final class LinearAlgebra {
  private LinearAlgebra(){}

  /**
   * Solve Ax=b using Gaussian elimination with partial pivoting.
   * Works for small/moderate n; for production use a numerical library.
   */
  public static double[] solve(double[][] A, double[] b) {
    int n = A.length;
    double[][] M = new double[n][n+1];
    for (int i = 0; i < n; i++) {
      System.arraycopy(A[i], 0, M[i], 0, n);
      M[i][n] = b[i];
    }

    for (int k = 0; k < n; k++) {
      // pivot
      int piv = k;
      double best = Math.abs(M[k][k]);
      for (int i = k+1; i < n; i++) {
        double v = Math.abs(M[i][k]);
        if (v > best) { best = v; piv = i; }
      }
      if (best < 1e-14) throw new IllegalArgumentException("Singular matrix (or ill-conditioned)");

      if (piv != k) {
        double[] tmp = M[k]; M[k] = M[piv]; M[piv] = tmp;
      }

      // normalize pivot row
      double div = M[k][k];
      for (int j = k; j <= n; j++) M[k][j] /= div;

      // eliminate
      for (int i = 0; i < n; i++) {
        if (i == k) continue;
        double f = M[i][k];
        for (int j = k; j <= n; j++) M[i][j] -= f * M[k][j];
      }
    }

    double[] x = new double[n];
    for (int i = 0; i < n; i++) x[i] = M[i][n];
    return x;
  }

  public static double[][] identity(int n) {
    double[][] I = new double[n][n];
    for (int i = 0; i < n; i++) I[i][i] = 1.0;
    return I;
  }

  public static double[][] add(double[][] A, double[][] B, double scaleB) {
    int n = A.length;
    double[][] C = new double[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) C[i][j] = A[i][j] + scaleB * B[i][j];
    }
    return C;
  }

  public static double[][] transpose(double[][] A) {
    int n = A.length;
    int m = A[0].length;
    double[][] T = new double[m][n];
    for (int i=0;i<n;i++) for(int j=0;j<m;j++) T[j][i]=A[i][j];
    return T;
  }

  public static double[][] matMul(double[][] A, double[][] B) {
    int n = A.length;
    int m = B[0].length;
    int k = B.length;
    double[][] C = new double[n][m];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < m; j++) {
        double s = 0.0;
        for (int t = 0; t < k; t++) s += A[i][t] * B[t][j];
        C[i][j] = s;
      }
    }
    return C;
  }

  public static double[] matVec(double[][] A, double[] x) {
    return MathUtils.matVec(A, x);
  }
}

package com.alphamath.portfolio.math;

import java.util.Arrays;

public final class MathUtils {
  private MathUtils(){}

  public static double dot(double[] a, double[] b) {
    double s = 0.0;
    for (int i = 0; i < a.length; i++) s += a[i] * b[i];
    return s;
  }

  public static double[] matVec(double[][] A, double[] x) {
    double[] y = new double[A.length];
    for (int i = 0; i < A.length; i++) {
      double s = 0.0;
      for (int j = 0; j < x.length; j++) s += A[i][j] * x[j];
      y[i] = s;
    }
    return y;
  }

  public static double quadForm(double[][] A, double[] x) {
    double[] Ax = matVec(A, x);
    return dot(x, Ax);
  }

  public static double[] add(double[] a, double[] b, double scaleB) {
    double[] y = new double[a.length];
    for (int i = 0; i < a.length; i++) y[i] = a[i] + scaleB * b[i];
    return y;
  }

  public static double l2norm(double[] x) {
    return Math.sqrt(dot(x, x));
  }

  public static double[][] toMatrix(java.util.List<java.util.List<Double>> cov) {
    int n = cov.size();
    double[][] A = new double[n][n];
    for (int i = 0; i < n; i++) {
      var row = cov.get(i);
      for (int j = 0; j < n; j++) A[i][j] = row.get(j);
    }
    return A;
  }

  public static double[] toVector(java.util.List<Double> v) {
    double[] x = new double[v.size()];
    for (int i = 0; i < v.size(); i++) x[i] = v.get(i);
    return x;
  }

  public static java.util.List<Double> toList(double[] x) {
    var out = new java.util.ArrayList<Double>(x.length);
    for (double v : x) out.add(v);
    return out;
  }

  /**
   * Projection onto the simplex with box constraints:
   *   minWeight <= w_i <= maxWeight and sum(w_i) = 1.
   * 
   * This uses iterative clipping + re-normalization (fast & robust for MVP).
   * For production, replace with a proper QP projection routine.
   */
  public static double[] projectToBoxedSimplex(double[] w, double minWeight, double maxWeight, int maxIters) {
    double[] x = Arrays.copyOf(w, w.length);
    for (int it = 0; it < maxIters; it++) {
      // clip
      double sum = 0.0;
      for (int i = 0; i < x.length; i++) {
        if (x[i] < minWeight) x[i] = minWeight;
        if (x[i] > maxWeight) x[i] = maxWeight;
        sum += x[i];
      }
      // normalize to sum=1 within free mass
      double diff = 1.0 - sum;
      if (Math.abs(diff) < 1e-10) return x;

      // distribute diff among non-saturated components
      int free = 0;
      for (int i = 0; i < x.length; i++) {
        if (diff > 0 && x[i] < maxWeight - 1e-12) free++;
        if (diff < 0 && x[i] > minWeight + 1e-12) free++;
      }
      if (free == 0) return x;

      double step = diff / free;
      for (int i = 0; i < x.length; i++) {
        if (diff > 0 && x[i] < maxWeight - 1e-12) x[i] += step;
        if (diff < 0 && x[i] > minWeight + 1e-12) x[i] += step;
      }
    }
    return x;
  }

  public static double[] equalWeights(int n) {
    double[] w = new double[n];
    double v = 1.0 / n;
    for (int i = 0; i < n; i++) w[i] = v;
    return w;
  }
}

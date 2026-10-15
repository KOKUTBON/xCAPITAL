package com.alphamath.portfolio.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

  @PostMapping("/metrics")
  public MetricsResponse metrics(@Valid @RequestBody MetricsRequest req) {
    return compute(req, false);
  }

  @PostMapping("/metrics/advanced")
  public MetricsResponse metricsAdvanced(@Valid @RequestBody MetricsRequest req) {
    return compute(req, true);
  }

  private MetricsResponse compute(MetricsRequest req, boolean advanced) {
    List<Double> r = req.returns;
    if (r.size() < 30) throw new IllegalArgumentException("Need at least 30 return observations");

    int n = r.size();
    double mean = r.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

    double m2 = 0.0, m3 = 0.0, m4 = 0.0;
    for (double x : r) {
      double d = x - mean;
      m2 += d*d;
      m3 += d*d*d;
      m4 += d*d*d*d;
    }
    double var = m2 / (n - 1);
    double std = Math.sqrt(Math.max(0.0, var));

    // downside deviation (threshold = riskFree or 0)
    double thr = req.downsideThreshold;
    double dd2 = 0.0;
    int ddCount = 0;
    for (double x : r) {
      double d = Math.min(0.0, x - thr);
      dd2 += d*d;
      ddCount++;
    }
    double downsideDev = Math.sqrt(dd2 / Math.max(1, ddCount - 1));

    double sharpe = (std == 0) ? 0 : (mean - req.riskFree) / std;
    double sortino = (downsideDev == 0) ? 0 : (mean - req.riskFree) / downsideDev;

    // Max drawdown from equity curve
    double eq = 1.0;
    double peak = 1.0;
    double maxDd = 0.0;
    for (double x : r) {
      eq *= (1.0 + x);
      peak = Math.max(peak, eq);
      double dd = (peak - eq) / peak;
      maxDd = Math.max(maxDd, dd);
    }
    double calmar = (maxDd == 0) ? 0 : (mean / maxDd);

    // Historical VaR & CVaR
    List<Double> losses = new ArrayList<>();
    for (double x : r) losses.add(-x); // loss = -return
    Collections.sort(losses);

    int idx = (int)Math.floor((1.0 - req.confidence) * losses.size());
    idx = Math.max(0, Math.min(idx, losses.size()-1));
    double varHist = losses.get(idx);

    double tailSum = 0.0;
    int tailCount = 0;
    for (int i = 0; i <= idx; i++) { tailSum += losses.get(i); tailCount++; }
    double cvarHist = tailCount == 0 ? varHist : (tailSum / tailCount);

    // Omega ratio at threshold
    double gain = 0.0, loss = 0.0;
    for (double x : r) {
      double a = x - thr;
      if (a > 0) gain += a;
      else loss += -a;
    }
    double omega = (loss == 0) ? (gain > 0 ? Double.POSITIVE_INFINITY : 1.0) : (gain / loss);

    // Skewness & excess kurtosis (moment-based)
    double skew = 0.0, exKurt = 0.0;
    if (std > 1e-12) {
      double s3 = m3 / n;
      double s4 = m4 / n;
      double s2 = m2 / n;
      double sigma = Math.sqrt(Math.max(1e-18, s2));
      skew = s3 / Math.pow(sigma, 3);
      exKurt = s4 / Math.pow(sigma, 4) - 3.0;
    }

    // Parametric Normal VaR/CVaR (approx)
    double z = invNorm(req.confidence);
    double varNorm = Math.max(0.0, -(mean + z * std));
    double cvarNorm = Math.max(0.0, -(mean + std * pdfNorm(z) / (1.0 - req.confidence)));

    // Cornish-Fisher adjusted VaR (skew/kurt correction)
    double zcf = z;
    if (advanced) {
      zcf = z + (1.0/6.0)*(z*z - 1.0)*skew
               + (1.0/24.0)*(z*z*z - 3.0*z)*exKurt
               - (1.0/36.0)*(2.0*z*z*z - 5.0*z)*skew*skew;
    }
    double varCF = Math.max(0.0, -(mean + zcf * std));

    return new MetricsResponse(
        mean, std, sharpe, sortino, maxDd, calmar,
        varHist, cvarHist, varNorm, cvarNorm, varCF,
        downsideDev, omega, skew, exKurt,
        "Risk metrics are decision-support analytics; not financial advice."
    );
  }

  // Abramowitz-Stegun approximation for inverse normal CDF (sufficient for risk tooling)
  private static double invNorm(double p) {
    if (p <= 0 || p >= 1) throw new IllegalArgumentException("p must be in (0,1)");
    // Coefficients (Peter John Acklam approximation)
    double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
        1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
    double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
        6.680131188771972e+01, -1.328068155288572e+01};
    double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
        -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
    double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
        3.754408661907416e+00};

    double plow = 0.02425;
    double phigh = 1 - plow;
    double q, r;

    if (p < plow) {
      q = Math.sqrt(-2 * Math.log(p));
      return (((((c[0]*q + c[1])*q + c[2])*q + c[3])*q + c[4])*q + c[5]) /
          ((((d[0]*q + d[1])*q + d[2])*q + d[3])*q + 1);
    }
    if (phigh < p) {
      q = Math.sqrt(-2 * Math.log(1 - p));
      return -(((((c[0]*q + c[1])*q + c[2])*q + c[3])*q + c[4])*q + c[5]) /
          ((((d[0]*q + d[1])*q + d[2])*q + d[3])*q + 1);
    }

    q = p - 0.5;
    r = q*q;
    return (((((a[0]*r + a[1])*r + a[2])*r + a[3])*r + a[4])*r + a[5]) * q /
        (((((b[0]*r + b[1])*r + b[2])*r + b[3])*r + b[4])*r + 1);
  }

  private static double pdfNorm(double z) {
    return Math.exp(-0.5*z*z) / Math.sqrt(2*Math.PI);
  }

  @Data
  public static class MetricsRequest {
    @NotNull @Size(min=30, max=200000)
    public List<@NotNull Double> returns;

    // risk-free per period
    public double riskFree = 0.0;

    // e.g., 0.95 for 95% VaR
    public double confidence = 0.95;

    // threshold for downside / omega
    public double downsideThreshold = 0.0;
  }

  @Data
  public static class MetricsResponse {
    public final double meanReturn;
    public final double stdDev;
    public final double sharpe;
    public final double sortino;
    public final double maxDrawdown;
    public final double calmar;

    public final double varHist;
    public final double cvarHist;
    public final double varNorm;
    public final double cvarNorm;
    public final double varCornishFisher;

    public final double downsideDev;
    public final double omegaRatio;
    public final double skewness;
    public final double excessKurtosis;

    public final String disclaimer;
  }
}

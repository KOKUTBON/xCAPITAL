package com.alphamath.portfolio.web;

import com.alphamath.portfolio.math.Optimizers;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/math")
public class FormulaController {

  @GetMapping("/formulas")
  public Map<String, Object> formulas() {
    Map<String, Object> out = new LinkedHashMap<>();

    out.put("optimizers", List.of(
        Optimizers.Method.RANDOM_MVP.name(),
        Optimizers.Method.MEAN_VARIANCE_PGD.name(),
        Optimizers.Method.MIN_VARIANCE.name(),
        Optimizers.Method.RISK_PARITY.name(),
        Optimizers.Method.KELLY_APPROX.name(),
        Optimizers.Method.BLACK_LITTERMAN_MEANVAR.name()
    ));

    out.put("riskMetrics", List.of(
        "Mean, StdDev, Sharpe, Sortino, MaxDrawdown, Calmar",
        "Historical VaR, Historical CVaR",
        "Parametric Normal VaR/CVaR (approx)",
        "Skewness, Kurtosis, DownsideDeviation, OmegaRatio"
    ));

    out.put("covarianceEstimators", List.of(
        "SampleCovariance",
        "EWMA Covariance (RiskMetrics)",
        "Shrinkage-to-Diagonal (robust default)"
    ));

    out.put("notes", List.of(
        "This is a growing math-engine registry. The code is modular so you can add more models without breaking the API.",
        "All outputs are decision-support analytics and do not constitute financial advice."
    ));

    out.put("livingRegistryEndpoint", "/api/v1/registry/formulas");
    return out;
  }
}

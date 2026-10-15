package com.alphamath.portfolio.application.policy;

import com.alphamath.portfolio.domain.execution.AssetClass;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Locale;

@Service
public class ProviderPolicyService {
  private static final String JPM_ID = "JP_MORGAN";
  private static final EnumSet<AssetClass> JPM_ASSET_CLASSES = EnumSet.of(
      AssetClass.EQUITY,
      AssetClass.ETF,
      AssetClass.FIXED_INCOME,
      AssetClass.MUTUAL_FUND,
      AssetClass.OPTIONS
  );

  public void enforceAssetClass(String providerId, AssetClass assetClass) {
    if (providerId == null || providerId.isBlank() || assetClass == null) return;
    if (isJpm(providerId) && !JPM_ASSET_CLASSES.contains(assetClass)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "JP Morgan does not support asset class " + assetClass.name());
    }
  }

  public double estimateCommission(String providerId, AssetClass assetClass,
                                   double quantity, double notional, double fallbackFeeBps) {
    if (isJpm(providerId)) {
      if (assetClass == AssetClass.OPTIONS) {
        return 0.65 * Math.max(0.0, quantity);
      }
      return 0.0;
    }
    if (notional <= 0.0) return 0.0;
    return notional * (fallbackFeeBps / 10000.0);
  }

  public boolean isJpm(String providerId) {
    return providerId != null && providerId.trim().toUpperCase(Locale.US).equals(JPM_ID);
  }
}

package com.alphamath.portfolio.application.portfolio;

import com.alphamath.portfolio.domain.portfolio.PortfolioBuilderHolding;
import com.alphamath.portfolio.domain.portfolio.PortfolioBuilderPosition;
import com.alphamath.portfolio.domain.portfolio.PortfolioBuilderRequest;
import com.alphamath.portfolio.domain.portfolio.PortfolioBuilderResult;
import com.alphamath.portfolio.infrastructure.persistence.ScreenerSecurityEntity;
import com.alphamath.portfolio.infrastructure.persistence.ScreenerSecurityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PortfolioBuilderService {
  private final ScreenerSecurityRepository securities;

  public PortfolioBuilderService(ScreenerSecurityRepository securities) {
    this.securities = securities;
  }

  public PortfolioBuilderResult analyze(PortfolioBuilderRequest req) {
    if (req == null || req.getHoldings() == null || req.getHoldings().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "holdings are required");
    }

    Map<String, ScreenerSecurityEntity> universe = new HashMap<>();
    for (ScreenerSecurityEntity entity : securities.findAll()) {
      universe.put(entity.getSymbol().toUpperCase(Locale.US), entity);
    }

    double totalValue = 0.0;
    List<PositionInput> inputs = new ArrayList<>();
    for (PortfolioBuilderHolding holding : req.getHoldings()) {
      if (holding == null || holding.getSymbol() == null || holding.getSymbol().isBlank()) continue;
      double qty = holding.getQuantity() == null ? 0.0 : holding.getQuantity();
      double price = holding.getPrice() == null ? 0.0 : holding.getPrice();
      if (qty == 0.0 || price <= 0.0) continue;
      double value = qty * price;
      totalValue += value;
      inputs.add(new PositionInput(holding.getSymbol().trim().toUpperCase(Locale.US), value));
    }

    if (totalValue <= 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "total value must be positive");
    }

    PortfolioBuilderResult result = new PortfolioBuilderResult();
    result.setTotalValue(totalValue);

    Map<String, Double> sectorWeights = result.getSectorWeights();
    Map<String, Double> assetWeights = result.getAssetClassWeights();

    double hhi = 0.0;
    List<PortfolioBuilderPosition> positions = new ArrayList<>();
    for (PositionInput input : inputs) {
      double weight = input.value / totalValue;
      hhi += weight * weight;

      ScreenerSecurityEntity security = universe.get(input.symbol);
      String sector = security == null || security.getSector() == null ? "Unknown" : security.getSector();
      String assetClass = security == null || security.getAssetClass() == null ? "Unknown" : security.getAssetClass();

      sectorWeights.put(sector, sectorWeights.getOrDefault(sector, 0.0) + weight);
      assetWeights.put(assetClass, assetWeights.getOrDefault(assetClass, 0.0) + weight);

      PortfolioBuilderPosition pos = new PortfolioBuilderPosition();
      pos.setSymbol(input.symbol);
      pos.setValue(input.value);
      pos.setWeight(weight);
      pos.setSector(sector);
      pos.setAssetClass(assetClass);
      positions.add(pos);
    }

    positions.sort(Comparator.comparingDouble(PortfolioBuilderPosition::getWeight).reversed());
    result.setPositions(positions);
    result.setConcentrationHhi(hhi);

    double diversificationScore = Math.max(0.0, Math.min(100.0, (1.0 - hhi) * 100.0));
    result.setDiversificationScore(diversificationScore);

    if (!positions.isEmpty() && positions.get(0).getWeight() > 0.35) {
      result.getNotes().add("High single-position concentration above 35%.");
    }
    if (sectorWeights.size() < 3) {
      result.getNotes().add("Sector diversification is limited. Consider adding additional sectors.");
    }
    if (assetWeights.size() < 2) {
      result.getNotes().add("Asset class diversification is limited. Consider adding bonds or funds.");
    }

    return result;
  }

  private static class PositionInput {
    private final String symbol;
    private final double value;

    private PositionInput(String symbol, double value) {
      this.symbol = symbol;
      this.value = value;
    }
  }
}

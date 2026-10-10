package com.alphamath.portfolio.application.screener;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reference.InstrumentType;
import com.alphamath.portfolio.domain.research.ResearchRating;
import com.alphamath.portfolio.domain.screener.ScreenerQueryRequest;
import com.alphamath.portfolio.domain.screener.ScreenerResult;
import com.alphamath.portfolio.domain.screener.ScreenerSecurity;
import com.alphamath.portfolio.infrastructure.persistence.ScreenerSecurityEntity;
import com.alphamath.portfolio.infrastructure.persistence.ScreenerSecurityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ScreenerService {
  private final ScreenerSecurityRepository securities;

  public ScreenerService(ScreenerSecurityRepository securities) {
    this.securities = securities;
  }

  public ScreenerResult query(ScreenerQueryRequest req) {
    ScreenerQueryRequest q = req == null ? new ScreenerQueryRequest() : req;
    int limit = q.getLimit() == null || q.getLimit() <= 0 ? 50 : Math.min(q.getLimit(), 200);

    List<ScreenerSecurity> results = new ArrayList<>();
    for (ScreenerSecurityEntity entity : securities.findAll()) {
      if (!matches(entity, q)) continue;
      results.add(toDto(entity));
      if (results.size() >= limit) break;
    }

    ScreenerResult out = new ScreenerResult();
    out.setResults(results);
    out.setTotal(results.size());
    return out;
  }

  private boolean matches(ScreenerSecurityEntity entity, ScreenerQueryRequest q) {
    if (q.getAssetClass() != null && entity.getAssetClass() != null) {
      if (!q.getAssetClass().name().equalsIgnoreCase(entity.getAssetClass())) return false;
    } else if (q.getAssetClass() != null && entity.getAssetClass() == null) {
      return false;
    }

    if (q.getInstrumentType() != null && entity.getInstrumentType() != null) {
      if (!q.getInstrumentType().name().equalsIgnoreCase(entity.getInstrumentType())) return false;
    } else if (q.getInstrumentType() != null && entity.getInstrumentType() == null) {
      return false;
    }

    if (q.getSector() != null && !q.getSector().isBlank()) {
      if (entity.getSector() == null || !entity.getSector().equalsIgnoreCase(q.getSector().trim())) return false;
    }

    if (q.getIndustry() != null && !q.getIndustry().isBlank()) {
      if (entity.getIndustry() == null || !entity.getIndustry().equalsIgnoreCase(q.getIndustry().trim())) return false;
    }

    if (q.getRating() != null) {
      if (entity.getRating() == null || !q.getRating().name().equalsIgnoreCase(entity.getRating())) return false;
    }

    if (q.getFocusList() != null) {
      if (entity.isFocusList() != q.getFocusList()) return false;
    }

    if (!inRange(entity.getMarketCap(), q.getMinMarketCap(), q.getMaxMarketCap())) return false;
    if (!inRange(entity.getPeRatio(), q.getMinPeRatio(), q.getMaxPeRatio())) return false;
    if (!inRange(entity.getDividendYield(), q.getMinDividendYield(), q.getMaxDividendYield())) return false;

    return true;
  }

  private boolean inRange(Double value, Double min, Double max) {
    if (min != null && (value == null || value < min)) return false;
    if (max != null && (value == null || value > max)) return false;
    return true;
  }

  private ScreenerSecurity toDto(ScreenerSecurityEntity entity) {
    ScreenerSecurity out = new ScreenerSecurity();
    out.setSymbol(entity.getSymbol());
    out.setName(entity.getName());
    out.setSector(entity.getSector());
    out.setIndustry(entity.getIndustry());
    out.setMarketCap(entity.getMarketCap());
    out.setPeRatio(entity.getPeRatio());
    out.setDividendYield(entity.getDividendYield());
    out.setAssetClass(entity.getAssetClass() == null ? null : AssetClass.valueOf(entity.getAssetClass().toUpperCase(Locale.US)));
    out.setInstrumentType(entity.getInstrumentType() == null ? null : InstrumentType.valueOf(entity.getInstrumentType().toUpperCase(Locale.US)));
    out.setCurrency(entity.getCurrency());
    out.setRating(entity.getRating() == null ? null : ResearchRating.valueOf(entity.getRating().toUpperCase(Locale.US)));
    out.setPriceTarget(entity.getPriceTarget());
    out.setFocusList(entity.isFocusList());
    return out;
  }
}

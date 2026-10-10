package com.alphamath.portfolio.domain.screener;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reference.InstrumentType;
import com.alphamath.portfolio.domain.research.ResearchRating;
import lombok.Data;

@Data
public class ScreenerSecurity {
  private String symbol;
  private String name;
  private String sector;
  private String industry;
  private Double marketCap;
  private Double peRatio;
  private Double dividendYield;
  private AssetClass assetClass;
  private InstrumentType instrumentType;
  private String currency;
  private ResearchRating rating;
  private Double priceTarget;
  private boolean focusList;
}

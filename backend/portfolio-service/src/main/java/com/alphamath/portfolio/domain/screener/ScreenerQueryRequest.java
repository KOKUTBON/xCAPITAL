package com.alphamath.portfolio.domain.screener;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reference.InstrumentType;
import com.alphamath.portfolio.domain.research.ResearchRating;
import lombok.Data;

@Data
public class ScreenerQueryRequest {
  private AssetClass assetClass;
  private InstrumentType instrumentType;
  private String sector;
  private String industry;
  private Double minMarketCap;
  private Double maxMarketCap;
  private Double minPeRatio;
  private Double maxPeRatio;
  private Double minDividendYield;
  private Double maxDividendYield;
  private ResearchRating rating;
  private Boolean focusList;
  private Integer limit = 50;
}

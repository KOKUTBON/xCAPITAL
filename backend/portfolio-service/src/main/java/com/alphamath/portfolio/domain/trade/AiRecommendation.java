package com.alphamath.portfolio.domain.trade;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecommendation {
  private double confidence;
  private String summary;
  private List<String> reasons = new ArrayList<>();
  private Double expectedReturn;
  private Double volatility;
  private Double pUp;
  private Integer horizon;
  private String model;
  private String disclaimer;
}

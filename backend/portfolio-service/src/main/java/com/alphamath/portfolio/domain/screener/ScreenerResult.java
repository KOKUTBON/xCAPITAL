package com.alphamath.portfolio.domain.screener;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScreenerResult {
  private int total;
  private List<ScreenerSecurity> results = new ArrayList<>();
}

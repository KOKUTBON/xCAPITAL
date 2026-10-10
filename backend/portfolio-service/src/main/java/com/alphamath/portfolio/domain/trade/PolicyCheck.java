package com.alphamath.portfolio.domain.trade;

import lombok.Data;

@Data
public class PolicyCheck {
  private String name;
  private CheckStatus status;
  private String detail;
}

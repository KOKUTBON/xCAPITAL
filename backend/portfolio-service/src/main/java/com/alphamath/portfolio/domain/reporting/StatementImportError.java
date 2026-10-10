package com.alphamath.portfolio.domain.reporting;

import lombok.Data;

@Data
public class StatementImportError {
  private int line;
  private String message;
}

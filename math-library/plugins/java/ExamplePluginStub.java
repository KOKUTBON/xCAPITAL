package com.alphamath.portfolio.mathlib;

import java.util.Map;

public class ExamplePluginStub implements FormulaPlugin {
  @Override public String id() { return "example_formula"; }
  @Override public String name() { return "Example Formula (Stub)"; }

  @Override
  public Map<String, Object> evaluate(Map<String, Object> inputs) {
    return Map.of("status", "stub", "message", "Implement me");
  }
}

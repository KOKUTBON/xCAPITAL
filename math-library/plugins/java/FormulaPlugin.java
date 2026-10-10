package com.alphamath.portfolio.mathlib;

import java.util.Map;

/**
 * Plugin interface for adding new mathematical modules without changing controllers.
 * 
 * Enterprise approach:
 * - Each plugin provides: metadata + evaluation method(s)
 * - Registry maps formula IDs -> plugin implementation
 */
public interface FormulaPlugin {

  /** Unique formula ID (must exist in formulas.json). */
  String id();

  /** Human-readable name. */
  String name();

  /**
   * Execute the formula with typed inputs.
   * For safety, enforce input validation and return structured outputs.
   */
  Map<String, Object> evaluate(Map<String, Object> inputs);
}

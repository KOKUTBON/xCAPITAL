package com.alphamath.simulation.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SimulationRequest {
  @NotNull @Size(min = 2, max = 200000)
  private List<@NotNull Double> returns;

  private Strategy strategy = Strategy.BUY_AND_HOLD;

  @Min(1)
  private Double initialCash = 10000.0;

  private Double contribution = 0.0;

  @Min(1)
  private Integer contributionEvery = 1;
}

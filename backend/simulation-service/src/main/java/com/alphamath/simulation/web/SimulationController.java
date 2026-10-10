package com.alphamath.simulation.web;

import com.alphamath.simulation.jobs.SimulationJobService;
import com.alphamath.simulation.model.SimulationCapacity;
import com.alphamath.simulation.model.SimulationJobResponse;
import com.alphamath.simulation.model.SimulationQuotaStatus;
import com.alphamath.simulation.model.SimulationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {
  private final SimulationJobService jobs;

  public SimulationController(SimulationJobService jobs) {
    this.jobs = jobs;
  }

  @PostMapping("/backtest")
  public SimulationJobResponse backtest(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                        @Valid @RequestBody SimulationRequest req) {
    return jobs.submit(userId, req);
  }

  @GetMapping("/backtest/{id}")
  public SimulationJobResponse get(@PathVariable String id) {
    SimulationJobResponse response = jobs.get(id);
    if (response == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Result not found");
    }
    return response;
  }

  @GetMapping("/capacity")
  public SimulationCapacity capacity() {
    return jobs.capacity();
  }

  @GetMapping("/quota")
  public SimulationQuotaStatus quota(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return jobs.quota(userId);
  }
}

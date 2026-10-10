package com.alphamath.simulation.jobs;

import com.alphamath.simulation.model.SimulationCapacity;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimulationMetrics {
  private final SimulationJobService jobs;
  private final AtomicLong queueDepth = new AtomicLong();
  private final AtomicLong pendingJobs = new AtomicLong();
  private final AtomicLong runningJobs = new AtomicLong();
  private final AtomicLong inFlight = new AtomicLong();
  private final AtomicLong maxConcurrency = new AtomicLong();

  public SimulationMetrics(SimulationJobService jobs, MeterRegistry registry) {
    this.jobs = jobs;
    registry.gauge("simulation.queue.depth", queueDepth);
    registry.gauge("simulation.jobs.pending", pendingJobs);
    registry.gauge("simulation.jobs.running", runningJobs);
    registry.gauge("simulation.worker.inflight", inFlight);
    registry.gauge("simulation.worker.max_concurrency", maxConcurrency);
    refresh();
  }

  @Scheduled(fixedDelayString = "${simulation.metrics.refresh-ms:5000}")
  public void refresh() {
    SimulationCapacity capacity = jobs.capacity();
    queueDepth.set(capacity.getQueueDepth());
    pendingJobs.set(capacity.getPendingJobs());
    runningJobs.set(capacity.getRunningJobs());
    inFlight.set(capacity.getInFlight());
    maxConcurrency.set(capacity.getMaxConcurrency());
  }
}

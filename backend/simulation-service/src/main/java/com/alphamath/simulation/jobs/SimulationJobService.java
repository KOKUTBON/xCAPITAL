package com.alphamath.simulation.jobs;

import com.alphamath.simulation.engine.SimulationEngine;
import com.alphamath.simulation.model.SimulationCapacity;
import com.alphamath.simulation.model.SimulationJobResponse;
import com.alphamath.simulation.model.SimulationJobStatus;
import com.alphamath.simulation.model.SimulationQuotaStatus;
import com.alphamath.simulation.model.SimulationRequest;
import com.alphamath.simulation.model.SimulationResult;
import com.alphamath.simulation.model.StrategyConfig;
import com.alphamath.simulation.persistence.JsonUtils;
import com.alphamath.simulation.persistence.SimulationJobEntity;
import com.alphamath.simulation.persistence.SimulationJobRepository;
import com.alphamath.simulation.persistence.SimulationStrategyConfigEntity;
import com.alphamath.simulation.persistence.SimulationStrategyConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;

@Service
public class SimulationJobService {
  private final SimulationJobRepository jobs;
  private final SimulationStrategyConfigRepository configs;
  private final SimulationEngine engine;
  private final SimulationQueue queue;
  private final SimulationWorkerProperties properties;
  private final SimulationQuotaProperties quotas;
  private final ExecutorService executor;
  private final AtomicInteger inFlight = new AtomicInteger(0);
  private final String workerId;

  public SimulationJobService(SimulationJobRepository jobs,
                              SimulationStrategyConfigRepository configs,
                              SimulationEngine engine,
                              SimulationQueue queue,
                              SimulationWorkerProperties properties,
                              SimulationQuotaProperties quotas) {
    this.jobs = jobs;
    this.configs = configs;
    this.engine = engine;
    this.queue = queue;
    this.properties = properties;
    this.quotas = quotas;
    this.workerId = "worker-" + UUID.randomUUID();
    this.executor = new ThreadPoolExecutor(
        properties.getMaxConcurrency(),
        properties.getMaxConcurrency(),
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(Math.max(1, properties.getExecutorQueueCapacity())),
        new NamedThreadFactory("simulation-worker")
    );
  }

  public SimulationJobResponse submit(String userId, SimulationRequest request) {
    String normalizedUser = normalizeUserId(userId);
    enforceQuota(normalizedUser);

    long pending = jobs.countByStatus(SimulationJobStatus.PENDING.name());
    if (pending >= properties.getMaxQueueDepth()) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Simulation queue is full");
    }
    SimulationStrategyConfigEntity config = resolveConfig(request);
    String returnsHash = hash(JsonUtils.toJson(request.getReturns()));

    SimulationJobEntity entity = new SimulationJobEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setStatus(SimulationJobStatus.PENDING.name());
    entity.setAttempts(0);
    entity.setRequestJson(JsonUtils.toJson(request));
    entity.setUserId(normalizedUser);
    entity.setStrategyConfigId(config.getId());
    entity.setStrategyConfigVersion(config.getVersion());
    entity.setReturnsHash(returnsHash);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    jobs.save(entity);
    queue.enqueue(entity.getId());
    return toResponse(entity, request, null, null);
  }

  public SimulationJobResponse get(String id) {
    SimulationJobEntity entity = jobs.findById(id).orElse(null);
    if (entity == null) return null;
    SimulationRequest request = JsonUtils.fromJson(entity.getRequestJson(), SimulationRequest.class);
    SimulationResult result = entity.getResultJson() == null ? null
        : JsonUtils.fromJson(entity.getResultJson(), SimulationResult.class);
    return toResponse(entity, request, result, entity.getError());
  }

  public SimulationQuotaStatus quota(String userId) {
    String normalizedUser = normalizeUserId(userId);
    long pending = jobs.countByStatusAndUserId(SimulationJobStatus.PENDING.name(), normalizedUser);
    long running = jobs.countByStatusAndUserId(SimulationJobStatus.RUNNING.name(), normalizedUser);

    SimulationQuotaStatus status = new SimulationQuotaStatus();
    status.setUserId(normalizedUser);
    status.setPending(pending);
    status.setRunning(running);
    status.setActive(pending + running);
    status.setMaxPending(quotas.getMaxPendingPerUser());
    status.setMaxRunning(quotas.getMaxRunningPerUser());
    status.setMaxActive(quotas.getMaxActivePerUser());
    status.setQueueDepth(queue.size());
    status.setMaxQueueDepth(properties.getMaxQueueDepth());
    status.setAsOf(Instant.now());
    return status;
  }

  @Scheduled(fixedDelayString = "${simulation.worker.delay-ms:1000}")
  public void work() {
    int available = Math.max(0, properties.getMaxConcurrency() - inFlight.get());
    if (available <= 0) {
      return;
    }
    int batchSize = Math.min(properties.getPollBatchSize(), available);
    List<SimulationQueue.QueuedJob> queued;
    try {
      queued = queue.poll(batchSize, Duration.ofMillis(properties.getBlockMs()));
    } catch (Exception e) {
      SimulationJobEntity fallback = jobs.findFirstByStatusOrderByCreatedAtAsc(SimulationJobStatus.PENDING.name())
          .orElse(null);
      if (fallback != null) {
        submitJob(fallback, null);
      }
      return;
    }
    if (queued.isEmpty()) return;

    for (SimulationQueue.QueuedJob job : queued) {
      SimulationJobEntity entity = jobs.findById(job.getJobId()).orElse(null);
      if (entity == null || !SimulationJobStatus.PENDING.name().equals(entity.getStatus())) {
        queue.ack(List.of(job.getRecordId()));
        continue;
      }

      submitJob(entity, job.getRecordId());
    }
  }

  @Scheduled(fixedDelayString = "${simulation.worker.recovery-delay-ms:60000}")
  public void recoverStaleJobs() {
    Instant cutoff = Instant.now().minus(Duration.ofMinutes(properties.getStaleAfterMinutes()));
    List<SimulationJobEntity> stale = jobs.findByStatusAndStartedAtBefore(SimulationJobStatus.RUNNING.name(), cutoff);
    if (stale.isEmpty()) {
      return;
    }
    for (SimulationJobEntity entity : stale) {
      if (entity.getAttempts() != null && entity.getAttempts() >= properties.getMaxAttempts()) {
        entity.setStatus(SimulationJobStatus.FAILED.name());
        entity.setError(entity.getLastError() == null ? "stale job exceeded max attempts" : entity.getLastError());
      } else {
        entity.setStatus(SimulationJobStatus.PENDING.name());
        entity.setStartedAt(null);
        entity.setWorkerId(null);
        queue.enqueue(entity.getId());
      }
      entity.setUpdatedAt(Instant.now());
      jobs.save(entity);
    }
  }

  public SimulationCapacity capacity() {
    SimulationCapacity capacity = new SimulationCapacity();
    capacity.setWorkerId(workerId);
    capacity.setMaxConcurrency(properties.getMaxConcurrency());
    capacity.setInFlight(inFlight.get());
    capacity.setExecutorQueueCapacity(properties.getExecutorQueueCapacity());
    capacity.setPollBatchSize(properties.getPollBatchSize());
    capacity.setBlockMs(properties.getBlockMs());
    capacity.setMaxQueueDepth(properties.getMaxQueueDepth());
    capacity.setQueueDepth(queue.size());
    capacity.setPendingJobs(jobs.countByStatus(SimulationJobStatus.PENDING.name()));
    capacity.setRunningJobs(jobs.countByStatus(SimulationJobStatus.RUNNING.name()));
    capacity.setAsOf(Instant.now());
    return capacity;
  }

  private void submitJob(SimulationJobEntity entity, org.springframework.data.redis.connection.stream.RecordId recordId) {
    if (!startJob(entity)) {
      if (recordId != null) {
        queue.ack(List.of(recordId));
      }
      return;
    }
    if (recordId != null) {
      queue.ack(List.of(recordId));
    }
    try {
      inFlight.incrementAndGet();
      executor.submit(() -> {
        try {
          processJob(entity);
        } finally {
          inFlight.decrementAndGet();
        }
      });
    } catch (Exception e) {
      inFlight.decrementAndGet();
      entity.setStatus(SimulationJobStatus.PENDING.name());
      entity.setStartedAt(null);
      entity.setWorkerId(null);
      entity.setUpdatedAt(Instant.now());
      jobs.save(entity);
      queue.enqueue(entity.getId());
    }
  }

  private boolean startJob(SimulationJobEntity entity) {
    int attempts = entity.getAttempts() == null ? 0 : entity.getAttempts();
    if (attempts >= properties.getMaxAttempts()) {
      entity.setStatus(SimulationJobStatus.FAILED.name());
      entity.setError(entity.getLastError() == null ? "max attempts reached" : entity.getLastError());
      entity.setUpdatedAt(Instant.now());
      jobs.save(entity);
      return false;
    }
    entity.setAttempts(attempts + 1);
    entity.setStatus(SimulationJobStatus.RUNNING.name());
    entity.setWorkerId(workerId);
    entity.setStartedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    jobs.save(entity);
    return true;
  }

  private void processJob(SimulationJobEntity entity) {
    try {
      SimulationRequest request = JsonUtils.fromJson(entity.getRequestJson(), SimulationRequest.class);
      SimulationResult result = engine.run(request);
      result.setId(entity.getId());
      entity.setResultJson(JsonUtils.toJson(result));
      entity.setStatus(SimulationJobStatus.COMPLETED.name());
      entity.setError(null);
      entity.setLastError(null);
    } catch (Exception e) {
      entity.setLastError(e.getMessage());
      if (entity.getAttempts() != null && entity.getAttempts() < properties.getMaxAttempts()) {
        entity.setStatus(SimulationJobStatus.PENDING.name());
        entity.setStartedAt(null);
        entity.setWorkerId(null);
        entity.setUpdatedAt(Instant.now());
        jobs.save(entity);
        queue.enqueue(entity.getId());
        return;
      }
      entity.setStatus(SimulationJobStatus.FAILED.name());
      entity.setError(e.getMessage());
    }
    entity.setUpdatedAt(Instant.now());
    jobs.save(entity);
  }

  private SimulationJobResponse toResponse(SimulationJobEntity entity, SimulationRequest request,
                                           SimulationResult result, String error) {
    SimulationJobResponse response = new SimulationJobResponse();
    response.setId(entity.getId());
    response.setStatus(SimulationJobStatus.valueOf(entity.getStatus()));
    response.setRequest(request);
    response.setResult(result);
    response.setError(error);
    response.setWorkerId(entity.getWorkerId());
    response.setAttempts(entity.getAttempts());
    response.setStartedAt(entity.getStartedAt());
    response.setLastError(entity.getLastError());
    response.setStrategyConfigId(entity.getStrategyConfigId());
    response.setStrategyConfigVersion(entity.getStrategyConfigVersion());
    response.setReturnsHash(entity.getReturnsHash());
    response.setCreatedAt(entity.getCreatedAt());
    response.setUpdatedAt(entity.getUpdatedAt());
    return response;
  }

  private void enforceQuota(String userId) {
    long pending = jobs.countByStatusAndUserId(SimulationJobStatus.PENDING.name(), userId);
    long running = jobs.countByStatusAndUserId(SimulationJobStatus.RUNNING.name(), userId);
    long active = pending + running;

    int maxPending = quotas.getMaxPendingPerUser();
    int maxRunning = quotas.getMaxRunningPerUser();
    int maxActive = quotas.getMaxActivePerUser();

    if (maxPending > 0 && pending >= maxPending) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Simulation pending quota exceeded");
    }
    if (maxRunning > 0 && running >= maxRunning) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Simulation running quota exceeded");
    }
    if (maxActive > 0 && active >= maxActive) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Simulation active quota exceeded");
    }
  }

  private String normalizeUserId(String userId) {
    if (userId == null) {
      return "anonymous";
    }
    String trimmed = userId.trim();
    if (trimmed.isEmpty()) {
      return "anonymous";
    }
    String lower = trimmed.toLowerCase();
    if (lower.equals("null") || lower.equals("undefined") || lower.equals("unknown")) {
      return "anonymous";
    }
    return trimmed;
  }

  private SimulationStrategyConfigEntity resolveConfig(SimulationRequest request) {
    StrategyConfig config = new StrategyConfig();
    config.setStrategy(request.getStrategy());
    config.setInitialCash(request.getInitialCash() == null ? 10000.0 : request.getInitialCash());
    config.setContribution(request.getContribution() == null ? 0.0 : request.getContribution());
    config.setContributionEvery(request.getContributionEvery() == null ? 1 : request.getContributionEvery());

    String json = JsonUtils.toJson(config);
    String hash = hash(json);
    SimulationStrategyConfigEntity existing = configs.findFirstByHash(hash).orElse(null);
    if (existing != null) {
      return existing;
    }

    SimulationStrategyConfigEntity entity = new SimulationStrategyConfigEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setStrategy(config.getStrategy().name());
    int nextVersion = configs.findTopByStrategyOrderByVersionDesc(config.getStrategy().name())
        .map(c -> c.getVersion() + 1)
        .orElse(1);
    entity.setVersion(nextVersion);
    entity.setHash(hash);
    entity.setConfigJson(json);
    entity.setCreatedAt(Instant.now());
    configs.save(entity);
    return entity;
  }

  private String hash(String payload) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      return Integer.toHexString(payload.hashCode());
    }
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdown();
  }

  private static class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    private NamedThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setName(prefix + "-" + counter.getAndIncrement());
      t.setDaemon(true);
      return t;
    }
  }
}

package com.alphamath.simulation.jobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SimulationQueue {
  private final StringRedisTemplate redis;
  private final String streamKey;
  private final String group;
  private final String consumer;

  public SimulationQueue(StringRedisTemplate redis,
                         @Value("${simulation.queue.stream:simulation_jobs}") String streamKey,
                         @Value("${simulation.queue.group:simulation-workers}") String group) {
    this.redis = redis;
    this.streamKey = streamKey;
    this.group = group;
    this.consumer = "worker-" + UUID.randomUUID();
  }

  public void enqueue(String jobId) {
    StreamOperations<String, Object, Object> ops = redis.opsForStream();
    ops.add(streamKey, Map.of("jobId", jobId));
  }

  public List<QueuedJob> poll(int count, Duration block) {
    ensureGroup();
    StreamReadOptions options = StreamReadOptions.empty().count(count).block(block);
    List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
        org.springframework.data.redis.connection.stream.Consumer.from(group, consumer),
        options,
        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
    );
    List<QueuedJob> out = new ArrayList<>();
    if (records == null) return out;
    for (MapRecord<String, Object, Object> record : records) {
      Object value = record.getValue().get("jobId");
      if (value == null) continue;
      out.add(new QueuedJob(record.getId(), value.toString()));
    }
    return out;
  }

  public void ack(List<RecordId> ids) {
    if (ids == null || ids.isEmpty()) return;
    redis.opsForStream().acknowledge(streamKey, group, ids.toArray(new RecordId[0]));
  }

  public long size() {
    try {
      Long size = redis.opsForStream().size(streamKey);
      return size == null ? 0L : size;
    } catch (Exception e) {
      return 0L;
    }
  }

  private void ensureGroup() {
    try {
      redis.opsForStream().createGroup(streamKey, ReadOffset.latest(), group);
    } catch (Exception ignored) {
    }
  }

  public static class QueuedJob {
    private final RecordId recordId;
    private final String jobId;

    public QueuedJob(RecordId recordId, String jobId) {
      this.recordId = recordId;
      this.jobId = jobId;
    }

    public RecordId getRecordId() {
      return recordId;
    }

    public String getJobId() {
      return jobId;
    }
  }
}

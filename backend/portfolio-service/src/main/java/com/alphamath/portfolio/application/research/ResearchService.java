package com.alphamath.portfolio.application.research;

import com.alphamath.portfolio.application.notification.NotificationService;
import com.alphamath.portfolio.application.marketdata.MarketDataService;
import com.alphamath.portfolio.domain.notification.NotificationType;
import com.alphamath.portfolio.domain.research.ResearchNote;
import com.alphamath.portfolio.domain.research.ResearchNoteRequest;
import com.alphamath.portfolio.infrastructure.ai.AiForecastService;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.ResearchNoteEntity;
import com.alphamath.portfolio.infrastructure.persistence.ResearchNoteRepository;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ResearchService {
  private static final List<String> POSITIVE = Arrays.asList(
      "beat", "growth", "upgrade", "bull", "surge", "outperform", "strong", "record", "profit", "buy"
  );
  private static final List<String> NEGATIVE = Arrays.asList(
      "miss", "downgrade", "bear", "decline", "risk", "lawsuit", "weak", "loss", "sell", "cut"
  );

  private final ResearchNoteRepository notes;
  private final MarketDataService marketData;
  private final AiForecastService ai;
  private final NotificationService notifications;
  private final TenantContext tenantContext;

  public ResearchService(ResearchNoteRepository notes,
                         MarketDataService marketData,
                         AiForecastService ai,
                         NotificationService notifications,
                         TenantContext tenantContext) {
    this.notes = notes;
    this.marketData = marketData;
    this.ai = ai;
    this.notifications = notifications;
    this.tenantContext = tenantContext;
  }

  public ResearchNote create(String userId, ResearchNoteRequest req) {
    if (req == null || req.getSource() == null || req.getSource().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source required");
    }
    if (req.getHeadline() == null || req.getHeadline().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "headline required");
    }

    ResearchNoteEntity entity = new ResearchNoteEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setSource(req.getSource().trim());
    entity.setHeadline(req.getHeadline().trim());
    entity.setSummary(req.getSummary());
    entity.setSymbolsJson(JsonUtils.toJson(normalizeSymbols(req.getSymbols())));
    entity.setSentimentScore(req.getSentimentScore());
    entity.setConfidence(req.getConfidence());
    entity.setPublishedAt(req.getPublishedAt());
    entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
    entity.setCreatedAt(Instant.now());

    applyAiSignals(entity, 120, 1);

    notes.save(entity);

    notifications.create(userId, NotificationType.RESEARCH_NOTE,
        "Research update", buildNotificationBody(entity),
        "portfolio_research_note", entity.getId(), Map.of("source", entity.getSource()));

    return toDto(entity);
  }

  public List<ResearchNote> list(String userId, String source, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    PageRequest page = PageRequest.of(0, size);
    String orgId = tenantContext.getOrgId();
    List<ResearchNoteEntity> rows;
    if (source != null && !source.isBlank()) {
      rows = orgId == null
          ? notes.findByUserIdAndSourceOrderByPublishedAtDesc(userId, source.trim(), page)
          : notes.findByUserIdAndOrgIdAndSourceOrderByPublishedAtDesc(userId, orgId, source.trim(), page);
    } else {
      rows = orgId == null
          ? notes.findByUserIdOrderByPublishedAtDesc(userId, page)
          : notes.findByUserIdAndOrgIdOrderByPublishedAtDesc(userId, orgId, page);
    }
    return rows.stream().map(this::toDto).toList();
  }

  public ResearchNote refreshAi(String userId, String id, int lookback, int horizon) {
    String orgId = tenantContext.getOrgId();
    ResearchNoteEntity entity = notes.findById(id).orElse(null);
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Research note not found");
    }
    applyAiSignals(entity, lookback, horizon);
    notes.save(entity);
    return toDto(entity);
  }

  public List<ResearchNote> refreshAll(String userId, int lookback, int horizon) {
    List<ResearchNote> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<ResearchNoteEntity> rows = orgId == null
        ? notes.findByUserIdOrderByPublishedAtDesc(userId, PageRequest.of(0, 200))
        : notes.findByUserIdAndOrgIdOrderByPublishedAtDesc(userId, orgId, PageRequest.of(0, 200));
    for (ResearchNoteEntity entity : rows) {
      applyAiSignals(entity, lookback, horizon);
      notes.save(entity);
      out.add(toDto(entity));
    }
    return out;
  }

  private void applyAiSignals(ResearchNoteEntity entity, int lookback, int horizon) {
    int safeLookback = lookback <= 0 ? 120 : Math.min(lookback, 365);
    int safeHorizon = horizon <= 0 ? 1 : Math.min(horizon, 30);

    double sentiment = entity.getSentimentScore() == null
        ? sentimentScore(entity.getHeadline() + " " + (entity.getSummary() == null ? "" : entity.getSummary()))
        : entity.getSentimentScore();
    entity.setSentimentScore(sentiment);

    List<String> symbols = parseSymbols(entity.getSymbolsJson());
    String symbol = symbols.isEmpty() ? null : symbols.get(0);
    List<Double> returns = symbol == null ? List.of() : marketData.returns(symbol, null, null, safeLookback);

    double momentum = avgReturn(returns);
    double momentumScore = clamp(momentum * 100.0 / 10.0, -1.0, 1.0);

    double vol = stdDev(returns);
    AiForecastService.AiRiskForecast risk = ai.risk(returns, safeHorizon);
    if (risk != null) {
      vol = risk.volatility();
    }

    double volPenalty = Math.min(1.0, Math.max(0.0, vol));
    double score = 50.0 + (20.0 * sentiment) + (15.0 * momentumScore) - (10.0 * volPenalty);
    score = clamp(score, 0.0, 100.0);
    entity.setAiScore(score);

    String regime = risk == null ? "UNKNOWN" : risk.regime();
    String summary = String.format(Locale.US,
        "Sentiment %.2f, momentum %.2f%%, vol %.4f, regime %s",
        sentiment, momentum * 100.0, vol, regime);
    entity.setAiSummary(summary);

    if (entity.getConfidence() == null) {
      entity.setConfidence(0.5 + 0.4 * Math.min(1.0, Math.abs(sentiment)));
    }
  }

  private String buildNotificationBody(ResearchNoteEntity entity) {
    String headline = entity.getHeadline();
    String source = entity.getSource();
    return source + ": " + headline;
  }

  private double sentimentScore(String text) {
    if (text == null || text.isBlank()) return 0.0;
    String lower = text.toLowerCase(Locale.US);
    int pos = 0;
    int neg = 0;
    for (String word : POSITIVE) {
      if (lower.contains(word)) pos++;
    }
    for (String word : NEGATIVE) {
      if (lower.contains(word)) neg++;
    }
    int total = pos + neg;
    if (total == 0) return 0.0;
    return (pos - neg) / (double) total;
  }

  private double avgReturn(List<Double> returns) {
    if (returns == null || returns.isEmpty()) return 0.0;
    double sum = 0.0;
    for (double r : returns) sum += r;
    return sum / returns.size();
  }

  private double stdDev(List<Double> returns) {
    if (returns == null || returns.size() < 2) return 0.0;
    double mean = avgReturn(returns);
    double m2 = 0.0;
    for (double r : returns) {
      double d = r - mean;
      m2 += d * d;
    }
    return Math.sqrt(m2 / (returns.size() - 1));
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private List<String> normalizeSymbols(List<String> symbols) {
    if (symbols == null) return new ArrayList<>();
    List<String> out = new ArrayList<>();
    for (String s : symbols) {
      if (s == null || s.isBlank()) continue;
      out.add(s.trim().toUpperCase(Locale.US));
    }
    return out;
  }

  private List<String> parseSymbols(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private ResearchNote toDto(ResearchNoteEntity entity) {
    ResearchNote out = new ResearchNote();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setSource(entity.getSource());
    out.setHeadline(entity.getHeadline());
    out.setSummary(entity.getSummary());
    out.setSymbols(parseSymbols(entity.getSymbolsJson()));
    out.setSentimentScore(entity.getSentimentScore());
    out.setConfidence(entity.getConfidence());
    out.setAiScore(entity.getAiScore());
    out.setAiSummary(entity.getAiSummary());
    out.setPublishedAt(entity.getPublishedAt());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private Map<String, Object> parseMetadata(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }
}

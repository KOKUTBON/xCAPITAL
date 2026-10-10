package com.alphamath.portfolio.application.watchlist;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.marketdata.MarketDataService;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.watchlist.Watchlist;
import com.alphamath.portfolio.domain.watchlist.WatchlistItem;
import com.alphamath.portfolio.domain.watchlist.WatchlistItemRequest;
import com.alphamath.portfolio.domain.watchlist.WatchlistRequest;
import com.alphamath.portfolio.infrastructure.ai.AiForecastService;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.WatchlistEntity;
import com.alphamath.portfolio.infrastructure.persistence.WatchlistItemEntity;
import com.alphamath.portfolio.infrastructure.persistence.WatchlistItemRepository;
import com.alphamath.portfolio.infrastructure.persistence.WatchlistRepository;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WatchlistService {
  private final WatchlistRepository watchlists;
  private final WatchlistItemRepository items;
  private final MarketDataService marketData;
  private final AiForecastService ai;
  private final AuditService audit;
  private final TenantContext tenantContext;

  public WatchlistService(WatchlistRepository watchlists,
                          WatchlistItemRepository items,
                          MarketDataService marketData,
                          AiForecastService ai,
                          AuditService audit,
                          TenantContext tenantContext) {
    this.watchlists = watchlists;
    this.items = items;
    this.marketData = marketData;
    this.ai = ai;
    this.audit = audit;
    this.tenantContext = tenantContext;
  }

  public Watchlist create(String userId, WatchlistRequest req) {
    if (req == null || req.getName() == null || req.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Watchlist name required");
    }
    WatchlistEntity entity = new WatchlistEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setName(req.getName().trim());
    entity.setDescription(req.getDescription());
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    watchlists.save(entity);

    audit.record(userId, userId, "WATCHLIST_CREATED", "portfolio_watchlist", entity.getId(),
        Map.of("name", entity.getName()));
    return toDto(entity);
  }

  public List<Watchlist> list(String userId) {
    List<Watchlist> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<WatchlistEntity> rows = orgId == null
        ? watchlists.findByUserIdOrderByUpdatedAtDesc(userId)
        : watchlists.findByUserIdAndOrgIdOrderByUpdatedAtDesc(userId, orgId);
    for (WatchlistEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  public Watchlist update(String userId, String id, WatchlistRequest req) {
    String orgId = tenantContext.getOrgId();
    WatchlistEntity entity = orgId == null
        ? watchlists.findByIdAndUserId(id, userId)
        : watchlists.findByIdAndUserIdAndOrgId(id, userId, orgId);
    if (entity == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found");
    }
    if (req.getName() != null && !req.getName().isBlank()) {
      entity.setName(req.getName().trim());
    }
    if (req.getDescription() != null) {
      entity.setDescription(req.getDescription());
    }
    entity.setUpdatedAt(Instant.now());
    watchlists.save(entity);
    audit.record(userId, userId, "WATCHLIST_UPDATED", "portfolio_watchlist", entity.getId(),
        Map.of("name", entity.getName()));
    return toDto(entity);
  }

  public void delete(String userId, String id) {
    String orgId = tenantContext.getOrgId();
    WatchlistEntity entity = orgId == null
        ? watchlists.findByIdAndUserId(id, userId)
        : watchlists.findByIdAndUserIdAndOrgId(id, userId, orgId);
    if (entity == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found");
    }
    items.deleteByWatchlistId(entity.getId());
    watchlists.delete(entity);
    audit.record(userId, userId, "WATCHLIST_DELETED", "portfolio_watchlist", entity.getId(),
        Map.of("name", entity.getName()));
  }

  public WatchlistItem addItem(String userId, String watchlistId, WatchlistItemRequest req) {
    String orgId = tenantContext.getOrgId();
    WatchlistEntity watchlist = orgId == null
        ? watchlists.findByIdAndUserId(watchlistId, userId)
        : watchlists.findByIdAndUserIdAndOrgId(watchlistId, userId, orgId);
    if (watchlist == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found");
    }
    if (req == null || req.getSymbol() == null || req.getSymbol().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbol required");
    }
    WatchlistItemEntity entity = new WatchlistItemEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setWatchlistId(watchlistId);
    entity.setSymbol(req.getSymbol().trim().toUpperCase(Locale.US));
    entity.setInstrumentId(req.getInstrumentId());
    entity.setAssetClass(parseAssetClass(req.getAssetClass()));
    entity.setNotes(req.getNotes());
    entity.setAiScore(req.getAiScore());
    entity.setAiSummary(req.getAiSummary());
    entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
    entity.setCreatedAt(Instant.now());
    items.save(entity);

    audit.record(userId, userId, "WATCHLIST_ITEM_ADDED", "portfolio_watchlist_item", entity.getId(),
        Map.of("symbol", entity.getSymbol(), "watchlistId", watchlistId));
    return toDto(entity);
  }

  public List<WatchlistItem> listItems(String userId, String watchlistId) {
    String orgId = tenantContext.getOrgId();
    WatchlistEntity watchlist = orgId == null
        ? watchlists.findByIdAndUserId(watchlistId, userId)
        : watchlists.findByIdAndUserIdAndOrgId(watchlistId, userId, orgId);
    if (watchlist == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found");
    }
    List<WatchlistItem> out = new ArrayList<>();
    for (WatchlistItemEntity entity : items.findByWatchlistIdOrderByCreatedAtDesc(watchlistId)) {
      out.add(toDto(entity));
    }
    return out;
  }

  public void removeItem(String userId, String watchlistId, String itemId) {
    String orgId = tenantContext.getOrgId();
    WatchlistEntity watchlist = orgId == null
        ? watchlists.findByIdAndUserId(watchlistId, userId)
        : watchlists.findByIdAndUserIdAndOrgId(watchlistId, userId, orgId);
    if (watchlist == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found");
    }
    WatchlistItemEntity entity = items.findById(itemId).orElse(null);
    if (entity == null || !watchlistId.equals(entity.getWatchlistId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist item not found");
    }
    items.delete(entity);
    audit.record(userId, userId, "WATCHLIST_ITEM_REMOVED", "portfolio_watchlist_item", entity.getId(),
        Map.of("symbol", entity.getSymbol(), "watchlistId", watchlistId));
  }

  public List<WatchlistItem> refreshInsights(String userId, String watchlistId, int horizon, int lookback) {
    String orgId = tenantContext.getOrgId();
    WatchlistEntity watchlist = orgId == null
        ? watchlists.findByIdAndUserId(watchlistId, userId)
        : watchlists.findByIdAndUserIdAndOrgId(watchlistId, userId, orgId);
    if (watchlist == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found");
    }
    int safeLookback = lookback <= 0 ? 120 : Math.min(lookback, 365);
    int safeHorizon = horizon <= 0 ? 1 : Math.min(horizon, 30);

    List<WatchlistItemEntity> rows = items.findByWatchlistIdOrderByCreatedAtDesc(watchlistId);
    for (WatchlistItemEntity entity : rows) {
      List<Double> returns = marketData.returns(entity.getSymbol(), null, null, safeLookback);
      AiForecastService.AiRiskForecast risk = ai.risk(returns, safeHorizon);
      if (risk == null) {
        entity.setAiScore(null);
        entity.setAiSummary("AI risk unavailable (need >=30 returns).");
      } else {
        double score = 100.0 / (1.0 + Math.max(0.0, risk.volatility()));
        entity.setAiScore(score);
        entity.setAiSummary(String.format(Locale.US,
            "AI risk: vol %.4f, maxDD %.4f, regime %s", risk.volatility(), risk.maxDrawdown(), risk.regime()));
      }
      items.save(entity);
    }

    audit.record(userId, userId, "WATCHLIST_AI_REFRESH", "portfolio_watchlist", watchlistId,
        Map.of("items", rows.size()));

    List<WatchlistItem> out = new ArrayList<>();
    for (WatchlistItemEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  private Watchlist toDto(WatchlistEntity entity) {
    Watchlist out = new Watchlist();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setName(entity.getName());
    out.setDescription(entity.getDescription());
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private WatchlistItem toDto(WatchlistItemEntity entity) {
    WatchlistItem out = new WatchlistItem();
    out.setId(entity.getId());
    out.setWatchlistId(entity.getWatchlistId());
    out.setSymbol(entity.getSymbol());
    out.setInstrumentId(entity.getInstrumentId());
    out.setAssetClass(entity.getAssetClass());
    out.setNotes(entity.getNotes());
    out.setAiScore(entity.getAiScore());
    out.setAiSummary(entity.getAiSummary());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private AssetClass parseAssetClass(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return AssetClass.valueOf(raw.trim().toUpperCase(Locale.US));
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

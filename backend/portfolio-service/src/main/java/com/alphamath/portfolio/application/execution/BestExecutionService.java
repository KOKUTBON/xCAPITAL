package com.alphamath.portfolio.application.execution;

import com.alphamath.portfolio.application.marketdata.LatestQuotesResult;
import com.alphamath.portfolio.application.marketdata.MarketDataService;
import com.alphamath.portfolio.domain.execution.BestExecutionRecord;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.trade.TradeOrder;
import com.alphamath.portfolio.infrastructure.persistence.BestExecutionEntity;
import com.alphamath.portfolio.infrastructure.persistence.BestExecutionRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BestExecutionService {
  private final BestExecutionRepository records;
  private final MarketDataService marketData;
  private final TenantContext tenantContext;

  public BestExecutionService(BestExecutionRepository records, MarketDataService marketData, TenantContext tenantContext) {
    this.records = records;
    this.marketData = marketData;
    this.tenantContext = tenantContext;
  }

  public List<BestExecutionRecord> record(String userId, String proposalId, List<TradeOrder> fills) {
    if (fills == null || fills.isEmpty()) return List.of();

    List<String> symbols = fills.stream().map(TradeOrder::getSymbol).distinct().toList();
    LatestQuotesResult quotes = marketData.latestQuotes(symbols);
    Map<String, MarketQuote> quoteMap = new LinkedHashMap<>();
    for (LatestQuotesResult.QuoteSnapshot snapshot : quotes.quotes()) {
      quoteMap.put(snapshot.quote().symbol(), snapshot.quote());
    }

    List<BestExecutionRecord> out = new ArrayList<>();
    for (TradeOrder fill : fills) {
      BestExecutionEntity entity = new BestExecutionEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setOrgId(tenantContext.getOrgId());
      entity.setProposalId(proposalId);
      entity.setSymbol(fill.getSymbol());
      entity.setSide(fill.getSide().name());
      entity.setRequestedPrice(fill.getPrice());
      entity.setExecutedPrice(fill.getPrice());
      MarketQuote quote = quoteMap.get(fill.getSymbol());
      if (quote != null && quote.price() > 0.0) {
        entity.setMarketPrice(quote.price());
        entity.setSlippageBps((fill.getPrice() - quote.price()) / quote.price() * 10000.0);
      }
      entity.setCreatedAt(Instant.now());
      records.save(entity);
      out.add(toDto(entity));
    }
    return out;
  }

  public List<BestExecutionRecord> list(String userId, String symbol, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    List<BestExecutionEntity> rows;
    String orgId = tenantContext.getOrgId();
    if (symbol != null && !symbol.isBlank()) {
      rows = orgId == null
          ? records.findByUserIdAndSymbolOrderByCreatedAtDesc(userId, symbol.trim().toUpperCase(), PageRequest.of(0, size))
          : records.findByUserIdAndOrgIdAndSymbolOrderByCreatedAtDesc(userId, orgId, symbol.trim().toUpperCase(), PageRequest.of(0, size));
    } else {
      rows = orgId == null
          ? records.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
          : records.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, PageRequest.of(0, size));
    }
    List<BestExecutionRecord> out = new ArrayList<>();
    for (BestExecutionEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  private BestExecutionRecord toDto(BestExecutionEntity entity) {
    BestExecutionRecord out = new BestExecutionRecord();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setProposalId(entity.getProposalId());
    out.setSymbol(entity.getSymbol());
    out.setSide(com.alphamath.portfolio.domain.trade.TradeSide.valueOf(entity.getSide()));
    out.setRequestedPrice(entity.getRequestedPrice());
    out.setExecutedPrice(entity.getExecutedPrice());
    out.setMarketPrice(entity.getMarketPrice());
    out.setSlippageBps(entity.getSlippageBps());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }
}

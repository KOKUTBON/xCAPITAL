package com.alphamath.portfolio.application.reporting;

import com.alphamath.portfolio.domain.reporting.PositionReconciliation;
import com.alphamath.portfolio.domain.reporting.ReconciliationReport;
import com.alphamath.portfolio.domain.reporting.ReconciliationRequest;
import com.alphamath.portfolio.domain.reporting.TaxLotSummary;
import com.alphamath.portfolio.domain.reporting.TaxLotStatus;
import com.alphamath.portfolio.domain.reporting.LedgerEntryType;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.LedgerEntryEntity;
import com.alphamath.portfolio.infrastructure.persistence.LedgerEntryRepository;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import com.alphamath.portfolio.infrastructure.persistence.TaxLotEntity;
import com.alphamath.portfolio.infrastructure.persistence.TaxLotRepository;
import com.alphamath.portfolio.security.TenantContext;
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
public class ReconciliationService {
  private static final double EPSILON = 1e-9;

  private final LedgerEntryRepository ledger;
  private final PortfolioPositionRepository positions;
  private final TaxLotRepository taxLots;
  private final TenantContext tenantContext;

  public ReconciliationService(LedgerEntryRepository ledger,
                               PortfolioPositionRepository positions,
                               TaxLotRepository taxLots,
                               TenantContext tenantContext) {
    this.ledger = ledger;
    this.positions = positions;
    this.taxLots = taxLots;
    this.tenantContext = tenantContext;
  }

  public ReconciliationReport reconcile(String userId, ReconciliationRequest req) {
    if (req == null || req.getAccountId() == null || req.getAccountId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    validateLotMethod(req.getLotMethod());
    String accountId = req.getAccountId();
    Instant now = Instant.now();
    String orgId = tenantContext.getOrgId();
    List<LedgerEntryEntity> entries = orgId == null
        ? ledger.findByUserIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(userId, accountId, Instant.EPOCH, now)
        : ledger.findByUserIdAndOrgIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(
            userId, orgId, accountId, Instant.EPOCH, now);

    Map<String, Double> ledgerPositions = rollupLedger(entries);
    Map<String, Double> positionMap = loadPositions(accountId);

    ReconciliationReport report = new ReconciliationReport();
    report.setAccountId(accountId);
    report.setAsOf(now);
    report.setLedgerEntryCount(entries.size());
    report.setPositionCount(positionMap.size());

    report.setPositions(buildPositionDiffs(ledgerPositions, positionMap));

    if (req.isApplyPositions()) {
      applyPositions(accountId, ledgerPositions);
      report.setPositionsApplied(true);
    }

    List<TaxLotEntity> lotEntities;
    if (req.isRebuildTaxLots()) {
      lotEntities = rebuildTaxLots(userId, accountId, entries, req.getLotMethod());
      report.setTaxLotsRebuilt(true);
    } else {
      lotEntities = orgId == null
          ? taxLots.findByUserIdAndAccountIdOrderByUpdatedAtDesc(userId, accountId)
          : taxLots.findByUserIdAndOrgIdAndAccountIdOrderByUpdatedAtDesc(userId, orgId, accountId);
    }

    report.setTaxLotCount(lotEntities.size());
    report.setTaxLots(buildTaxLotSummary(lotEntities));
    return report;
  }

  private Map<String, Double> rollupLedger(List<LedgerEntryEntity> entries) {
    Map<String, Double> out = new LinkedHashMap<>();
    for (LedgerEntryEntity entry : entries) {
      LedgerEntryType type = parseEntryType(entry.getEntryType());
      if (type != LedgerEntryType.BUY && type != LedgerEntryType.SELL) {
        continue;
      }
      String symbol = normalizeSymbol(entry.getSymbol());
      if (symbol == null) {
        continue;
      }
      double qty = entry.getQuantity() == null ? 0.0 : entry.getQuantity();
      double signed = type == LedgerEntryType.BUY ? qty : -qty;
      out.put(symbol, out.getOrDefault(symbol, 0.0) + signed);
    }
    return out;
  }

  private Map<String, Double> loadPositions(String accountId) {
    Map<String, Double> out = new LinkedHashMap<>();
    for (PortfolioPositionEntity entity : positions.findByAccountIdOrderBySymbolAsc(accountId)) {
      out.put(entity.getSymbol(), entity.getQuantity());
    }
    return out;
  }

  private List<PositionReconciliation> buildPositionDiffs(Map<String, Double> ledgerPositions,
                                                           Map<String, Double> positionMap) {
    Map<String, Double> all = new LinkedHashMap<>(ledgerPositions);
    for (String symbol : positionMap.keySet()) {
      all.putIfAbsent(symbol, positionMap.get(symbol));
    }
    List<PositionReconciliation> out = new ArrayList<>();
    for (String symbol : all.keySet()) {
      double ledgerQty = ledgerPositions.getOrDefault(symbol, 0.0);
      double posQty = positionMap.getOrDefault(symbol, 0.0);
      PositionReconciliation diff = new PositionReconciliation();
      diff.setSymbol(symbol);
      diff.setLedgerQuantity(ledgerQty);
      diff.setPositionQuantity(posQty);
      diff.setDelta(ledgerQty - posQty);
      out.add(diff);
    }
    return out;
  }

  private void applyPositions(String accountId, Map<String, Double> ledgerPositions) {
    positions.deleteByAccountId(accountId);
    List<PortfolioPositionEntity> batch = new ArrayList<>();
    Instant now = Instant.now();
    for (Map.Entry<String, Double> entry : ledgerPositions.entrySet()) {
      double qty = entry.getValue() == null ? 0.0 : entry.getValue();
      if (Math.abs(qty) <= EPSILON) {
        continue;
      }
      PortfolioPositionEntity entity = new PortfolioPositionEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setAccountId(accountId);
      entity.setSymbol(entry.getKey());
      entity.setQuantity(qty);
      entity.setUpdatedAt(now);
      batch.add(entity);
    }
    positions.saveAll(batch);
  }

  private List<TaxLotEntity> rebuildTaxLots(String userId, String accountId,
                                            List<LedgerEntryEntity> entries, String method) {
    List<LotState> open = new ArrayList<>();
    List<LotState> closed = new ArrayList<>();

    for (LedgerEntryEntity entry : entries) {
      LedgerEntryType type = parseEntryType(entry.getEntryType());
      if (type != LedgerEntryType.BUY && type != LedgerEntryType.SELL) {
        continue;
      }
      String symbol = normalizeSymbol(entry.getSymbol());
      if (symbol == null) {
        continue;
      }
      double qty = entry.getQuantity() == null ? 0.0 : Math.abs(entry.getQuantity());
      if (qty <= EPSILON) {
        continue;
      }
      Instant tradeDate = entry.getTradeDate() == null ? Instant.now() : entry.getTradeDate();
      if (type == LedgerEntryType.BUY) {
        LotState lot = new LotState(symbol, qty, resolveCostPerUnit(entry), tradeDate);
        open.add(lot);
        continue;
      }

      double remaining = qty;
      List<LotState> symbolLots = new ArrayList<>();
      for (LotState lot : open) {
        if (lot.symbol.equals(symbol)) {
          symbolLots.add(lot);
        }
      }
      int idx = 0;
      while (remaining > EPSILON && idx < symbolLots.size()) {
        LotState lot = symbolLots.get(idx);
        double consumed = Math.min(remaining, lot.remainingQuantity);
        lot.remainingQuantity -= consumed;
        remaining -= consumed;
        if (lot.remainingQuantity <= EPSILON) {
          lot.remainingQuantity = 0.0;
          lot.disposedAt = tradeDate;
          closed.add(lot);
        }
        idx++;
      }
      open.removeIf(l -> l.symbol.equals(symbol) && l.remainingQuantity <= EPSILON);
      if (remaining > EPSILON) {
        LotState shortLot = new LotState(symbol, -remaining, 0.0, tradeDate);
        shortLot.shortPosition = true;
        open.add(shortLot);
      }
    }

    List<TaxLotEntity> allLots = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    allLots.addAll(toEntities(userId, orgId, accountId, open));
    allLots.addAll(toEntities(userId, orgId, accountId, closed));

    if (orgId == null) {
      taxLots.deleteByUserIdAndAccountId(userId, accountId);
    } else {
      taxLots.deleteByUserIdAndOrgIdAndAccountId(userId, orgId, accountId);
    }
    taxLots.saveAll(allLots);
    return allLots;
  }

  private void validateLotMethod(String method) {
    if (method == null || method.isBlank()) {
      return;
    }
    String normalized = method.trim().toUpperCase(Locale.US);
    if (!"FIFO".equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lotMethod must be FIFO");
    }
  }

  private List<TaxLotEntity> toEntities(String userId, String orgId, String accountId, List<LotState> lots) {
    List<TaxLotEntity> out = new ArrayList<>();
    Instant now = Instant.now();
    for (LotState lot : lots) {
      TaxLotEntity entity = new TaxLotEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setOrgId(orgId);
      entity.setAccountId(accountId);
      entity.setSymbol(lot.symbol);
      entity.setQuantity(lot.remainingQuantity);
      entity.setCostPerUnit(lot.costPerUnit);
      entity.setCostBasis(lot.remainingQuantity * lot.costPerUnit);
      entity.setAcquiredAt(lot.acquiredAt);
      entity.setDisposedAt(lot.disposedAt);
      entity.setStatus(Math.abs(lot.remainingQuantity) > EPSILON ? TaxLotStatus.OPEN : TaxLotStatus.CLOSED);
      entity.setMetadataJson(JsonUtils.toJson(Map.of(
          "originalQuantity", lot.originalQuantity,
          "shortPosition", lot.shortPosition
      )));
      entity.setUpdatedAt(now);
      entity.setCreatedAt(now);
      out.add(entity);
    }
    return out;
  }

  private List<TaxLotSummary> buildTaxLotSummary(List<TaxLotEntity> lots) {
    Map<String, TaxLotSummary> summary = new LinkedHashMap<>();
    for (TaxLotEntity lot : lots) {
      String symbol = lot.getSymbol();
      TaxLotSummary entry = summary.computeIfAbsent(symbol, k -> {
        TaxLotSummary s = new TaxLotSummary();
        s.setSymbol(k);
        return s;
      });
      if (TaxLotStatus.OPEN.equals(lot.getStatus())) {
        entry.setOpenLots(entry.getOpenLots() + 1);
        entry.setOpenQuantity(entry.getOpenQuantity() + lot.getQuantity());
        entry.setOpenCostBasis(entry.getOpenCostBasis() + (lot.getCostBasis() == null ? 0.0 : lot.getCostBasis()));
      } else {
        entry.setClosedLots(entry.getClosedLots() + 1);
      }
    }
    return new ArrayList<>(summary.values());
  }

  private LedgerEntryType parseEntryType(LedgerEntryType raw) {
    return raw == null ? LedgerEntryType.ADJUSTMENT : raw;
  }

  private String normalizeSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    return symbol.trim().toUpperCase(Locale.US);
  }

  private double resolveCostPerUnit(LedgerEntryEntity entry) {
    if (entry.getPrice() != null && entry.getPrice() > 0) {
      return entry.getPrice();
    }
    if (entry.getAmount() != null && entry.getQuantity() != null && entry.getQuantity() != 0) {
      return Math.abs(entry.getAmount() / entry.getQuantity());
    }
    return 0.0;
  }

  private static class LotState {
    final String symbol;
    final double originalQuantity;
    double remainingQuantity;
    final double costPerUnit;
    final Instant acquiredAt;
    Instant disposedAt;
    boolean shortPosition;

    LotState(String symbol, double quantity, double costPerUnit, Instant acquiredAt) {
      this.symbol = symbol;
      this.originalQuantity = quantity;
      this.remainingQuantity = quantity;
      this.costPerUnit = costPerUnit;
      this.acquiredAt = acquiredAt;
    }
  }
}

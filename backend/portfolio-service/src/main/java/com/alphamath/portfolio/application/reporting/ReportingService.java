package com.alphamath.portfolio.application.reporting;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reporting.CorporateAction;
import com.alphamath.portfolio.domain.reporting.CorporateActionRequest;
import com.alphamath.portfolio.domain.reporting.CorporateActionType;
import com.alphamath.portfolio.domain.reporting.LedgerEntry;
import com.alphamath.portfolio.domain.reporting.LedgerEntryRequest;
import com.alphamath.portfolio.domain.reporting.LedgerEntryType;
import com.alphamath.portfolio.domain.reporting.Statement;
import com.alphamath.portfolio.domain.reporting.StatementRequest;
import com.alphamath.portfolio.domain.reporting.TaxLot;
import com.alphamath.portfolio.domain.reporting.TaxLotRequest;
import com.alphamath.portfolio.domain.reporting.TaxLotStatus;
import com.alphamath.portfolio.infrastructure.persistence.CorporateActionEntity;
import com.alphamath.portfolio.infrastructure.persistence.CorporateActionRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.LedgerEntryEntity;
import com.alphamath.portfolio.infrastructure.persistence.LedgerEntryRepository;
import com.alphamath.portfolio.infrastructure.persistence.StatementEntity;
import com.alphamath.portfolio.infrastructure.persistence.StatementRepository;
import com.alphamath.portfolio.infrastructure.persistence.TaxLotEntity;
import com.alphamath.portfolio.infrastructure.persistence.TaxLotRepository;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.PageRequest;
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
public class ReportingService {
  private final LedgerEntryRepository ledger;
  private final TaxLotRepository taxLots;
  private final CorporateActionRepository corporateActions;
  private final StatementRepository statements;
  private final TenantContext tenantContext;

  public ReportingService(LedgerEntryRepository ledger,
                          TaxLotRepository taxLots,
                          CorporateActionRepository corporateActions,
                          StatementRepository statements,
                          TenantContext tenantContext) {
    this.ledger = ledger;
    this.taxLots = taxLots;
    this.corporateActions = corporateActions;
    this.statements = statements;
    this.tenantContext = tenantContext;
  }

  public List<LedgerEntry> addLedgerEntries(String userId, List<LedgerEntryRequest> reqs) {
    if (reqs == null || reqs.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ledger entries required");
    }
    List<LedgerEntry> out = new ArrayList<>();
    Instant now = Instant.now();
    String orgId = tenantContext.getOrgId();
    for (LedgerEntryRequest req : reqs) {
      if (req.getAccountId() == null || req.getAccountId().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
      }
      LedgerEntryEntity entity = new LedgerEntryEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setOrgId(orgId);
      entity.setAccountId(req.getAccountId());
      entity.setBrokerAccountId(req.getBrokerAccountId());
      entity.setEntryType(parseEntryType(req.getEntryType()));
      entity.setSymbol(normalizeSymbol(req.getSymbol()));
      entity.setInstrumentId(req.getInstrumentId());
      entity.setAssetClass(parseAssetClass(req.getAssetClass()));
      entity.setQuantity(req.getQuantity());
      entity.setPrice(req.getPrice());
      entity.setAmount(resolveAmount(req));
      entity.setCurrency(req.getCurrency());
      entity.setFxRate(req.getFxRate());
      entity.setTradeDate(req.getTradeDate() == null ? now : req.getTradeDate());
      entity.setSettleDate(req.getSettleDate());
      entity.setDescription(req.getDescription());
      entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
      entity.setCreatedAt(now);
      ledger.save(entity);
      out.add(toDto(entity));
    }
    return out;
  }

  public List<LedgerEntry> listLedgerEntries(String userId, String accountId, Instant start, Instant end, int limit) {
    if (accountId == null || accountId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    String orgId = tenantContext.getOrgId();
    if (start != null || end != null) {
      Instant startTs = start == null ? Instant.EPOCH : start;
      Instant endTs = end == null ? Instant.now() : end;
      List<LedgerEntryEntity> rows = orgId == null
          ? ledger.findByUserIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(userId, accountId, startTs, endTs)
          : ledger.findByUserIdAndOrgIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(userId, orgId, accountId, startTs,
              endTs);
      return rows.stream().map(this::toDto).toList();
    }
    int size = limit <= 0 ? 100 : Math.min(limit, 500);
    PageRequest page = PageRequest.of(0, size);
    List<LedgerEntryEntity> rows = orgId == null
        ? ledger.findByUserIdAndAccountIdOrderByTradeDateDesc(userId, accountId, page)
        : ledger.findByUserIdAndOrgIdAndAccountIdOrderByTradeDateDesc(userId, orgId, accountId, page);
    return rows.stream().map(this::toDto).toList();
  }

  public TaxLot upsertTaxLot(String userId, TaxLotRequest req) {
    if (req == null || req.getAccountId() == null || req.getAccountId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    if (req.getSymbol() == null || req.getSymbol().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol required");
    }

    String orgId = tenantContext.getOrgId();
    TaxLotEntity entity = null;
    if (req.getId() != null && !req.getId().isBlank()) {
      entity = taxLots.findById(req.getId()).orElse(null);
      if (entity != null && (!userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId())))) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax lot not found");
      }
    }
    if (entity == null) {
      entity = new TaxLotEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setOrgId(orgId);
      entity.setCreatedAt(Instant.now());
    }

    entity.setAccountId(req.getAccountId());
    entity.setSymbol(normalizeSymbol(req.getSymbol()));
    entity.setInstrumentId(req.getInstrumentId());
    entity.setAssetClass(parseAssetClass(req.getAssetClass()));
    entity.setQuantity(req.getQuantity() == null ? entity.getQuantity() : req.getQuantity());
    entity.setCostBasis(req.getCostBasis());
    entity.setCostPerUnit(resolveCostPerUnit(entity, req));
    entity.setAcquiredAt(req.getAcquiredAt());
    entity.setDisposedAt(req.getDisposedAt());
    entity.setStatus(parseStatus(req.getStatus()));
    entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
    entity.setUpdatedAt(Instant.now());
    taxLots.save(entity);
    return toDto(entity);
  }

  public List<TaxLot> listTaxLots(String userId, String accountId, String symbol, String status, int limit) {
    if (accountId == null || accountId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    int size = limit <= 0 ? 100 : Math.min(limit, 500);
    PageRequest page = PageRequest.of(0, size);

    String orgId = tenantContext.getOrgId();
    List<TaxLotEntity> rows;
    if (symbol != null && !symbol.isBlank()) {
      rows = orgId == null
          ? taxLots.findByUserIdAndAccountIdAndSymbolOrderByUpdatedAtDesc(userId, accountId, normalizeSymbol(symbol), page)
          : taxLots.findByUserIdAndOrgIdAndAccountIdAndSymbolOrderByUpdatedAtDesc(
              userId, orgId, accountId, normalizeSymbol(symbol), page);
    } else if (status != null && !status.isBlank()) {
      rows = orgId == null
          ? taxLots.findByUserIdAndAccountIdAndStatusOrderByUpdatedAtDesc(userId, accountId, parseStatus(status), page)
          : taxLots.findByUserIdAndOrgIdAndAccountIdAndStatusOrderByUpdatedAtDesc(
              userId, orgId, accountId, parseStatus(status), page);
    } else {
      rows = orgId == null
          ? taxLots.findByUserIdAndAccountIdOrderByUpdatedAtDesc(userId, accountId, page)
          : taxLots.findByUserIdAndOrgIdAndAccountIdOrderByUpdatedAtDesc(userId, orgId, accountId, page);
    }
    return rows.stream().map(this::toDto).toList();
  }

  public CorporateAction addCorporateAction(String userId, CorporateActionRequest req) {
    if (req == null || req.getActionType() == null || req.getActionType().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actionType required");
    }
    CorporateActionEntity entity = new CorporateActionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setAccountId(req.getAccountId());
    entity.setActionType(parseCorporateActionType(req.getActionType()));
    entity.setSymbol(normalizeSymbol(req.getSymbol()));
    entity.setInstrumentId(req.getInstrumentId());
    entity.setRatio(req.getRatio());
    entity.setCashAmount(req.getCashAmount());
    entity.setEffectiveDate(req.getEffectiveDate());
    entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
    entity.setCreatedAt(Instant.now());
    corporateActions.save(entity);
    return toDto(entity);
  }

  public List<CorporateAction> listCorporateActions(String userId, String symbol, int limit) {
    int size = limit <= 0 ? 100 : Math.min(limit, 500);
    PageRequest page = PageRequest.of(0, size);
    String orgId = tenantContext.getOrgId();
    List<CorporateActionEntity> rows;
    if (symbol != null && !symbol.isBlank()) {
      rows = orgId == null
          ? corporateActions.findByUserIdAndSymbolOrderByCreatedAtDesc(userId, normalizeSymbol(symbol), page)
          : corporateActions.findByUserIdAndOrgIdAndSymbolOrderByCreatedAtDesc(userId, orgId, normalizeSymbol(symbol), page);
    } else {
      rows = orgId == null
          ? corporateActions.findByUserIdOrderByCreatedAtDesc(userId, page)
          : corporateActions.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, page);
    }
    return rows.stream().map(this::toDto).toList();
  }

  public Statement generateStatement(String userId, StatementRequest req) {
    if (req == null || req.getAccountId() == null || req.getAccountId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    String orgId = tenantContext.getOrgId();
    Instant start = req.getPeriodStart() == null ? Instant.EPOCH : req.getPeriodStart();
    Instant end = req.getPeriodEnd() == null ? Instant.now() : req.getPeriodEnd();
    List<LedgerEntryEntity> rows = orgId == null
        ? ledger.findByUserIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(userId, req.getAccountId(), start, end)
        : ledger.findByUserIdAndOrgIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(
            userId, orgId, req.getAccountId(), start, end);

    StatementSummary summary = summarize(rows, req.getStartingBalance());

    StatementEntity entity = new StatementEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(orgId);
    entity.setAccountId(req.getAccountId());
    entity.setPeriodStart(start);
    entity.setPeriodEnd(end);
    entity.setBaseCurrency(req.getBaseCurrency() == null ? "USD" : req.getBaseCurrency());
    entity.setStartingBalance(summary.startingBalance());
    entity.setEndingBalance(summary.endingBalance());
    entity.setDeposits(summary.deposits());
    entity.setWithdrawals(summary.withdrawals());
    entity.setDividends(summary.dividends());
    entity.setFees(summary.fees());
    entity.setRealizedPnl(0.0);
    entity.setUnrealizedPnl(0.0);
    entity.setNetCashFlow(summary.netCashFlow());
    entity.setTradeNotional(summary.tradeNotional());
    entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
    entity.setCreatedAt(Instant.now());
    statements.save(entity);

    return toDto(entity);
  }

  public List<Statement> listStatements(String userId, String accountId, int limit) {
    if (accountId == null || accountId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    String orgId = tenantContext.getOrgId();
    int size = limit <= 0 ? 24 : Math.min(limit, 120);
    PageRequest page = PageRequest.of(0, size);
    List<StatementEntity> rows = orgId == null
        ? statements.findByUserIdAndAccountIdOrderByPeriodEndDesc(userId, accountId, page)
        : statements.findByUserIdAndOrgIdAndAccountIdOrderByPeriodEndDesc(userId, orgId, accountId, page);
    return rows.stream().map(this::toDto).toList();
  }

  public Map<String, Object> statementSummary(String userId, String accountId, Instant start, Instant end) {
    if (accountId == null || accountId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    String orgId = tenantContext.getOrgId();
    List<LedgerEntryEntity> rows = orgId == null
        ? ledger.findByUserIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(
            userId, accountId, start == null ? Instant.EPOCH : start, end == null ? Instant.now() : end)
        : ledger.findByUserIdAndOrgIdAndAccountIdAndTradeDateBetweenOrderByTradeDateAsc(
            userId, orgId, accountId, start == null ? Instant.EPOCH : start, end == null ? Instant.now() : end);
    StatementSummary summary = summarize(rows, null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("accountId", accountId);
    out.put("periodStart", start);
    out.put("periodEnd", end);
    out.put("deposits", summary.deposits());
    out.put("withdrawals", summary.withdrawals());
    out.put("dividends", summary.dividends());
    out.put("fees", summary.fees());
    out.put("netCashFlow", summary.netCashFlow());
    out.put("tradeNotional", summary.tradeNotional());
    out.put("tradeCount", summary.tradeCount());
    return out;
  }

  private StatementSummary summarize(List<LedgerEntryEntity> rows, Double startingBalance) {
    double deposits = 0.0;
    double withdrawals = 0.0;
    double dividends = 0.0;
    double fees = 0.0;
    double tradeNotional = 0.0;
    int tradeCount = 0;

    for (LedgerEntryEntity row : rows) {
      double amount = row.getAmount() == null ? 0.0 : Math.abs(row.getAmount());
      if (row.getEntryType() == null) continue;
      switch (row.getEntryType()) {
        case DEPOSIT -> deposits += amount;
        case WITHDRAWAL -> withdrawals += amount;
        case DIVIDEND, INTEREST -> dividends += amount;
        case FEE -> fees += amount;
        case BUY, SELL -> {
          tradeNotional += amount;
          tradeCount += 1;
        }
        default -> {
          // ignore for summary
        }
      }
    }

    double start = startingBalance == null ? 0.0 : startingBalance;
    double netCashFlow = deposits - withdrawals - fees + dividends;
    double end = start + netCashFlow;

    return new StatementSummary(start, end, deposits, withdrawals, dividends, fees, netCashFlow, tradeNotional, tradeCount);
  }

  private LedgerEntryType parseEntryType(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryType required");
    }
    return LedgerEntryType.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private CorporateActionType parseCorporateActionType(String raw) {
    return CorporateActionType.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private TaxLotStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) return TaxLotStatus.OPEN;
    return TaxLotStatus.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private AssetClass parseAssetClass(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return AssetClass.valueOf(raw.trim().toUpperCase(Locale.US));
  }

  private Double resolveAmount(LedgerEntryRequest req) {
    if (req.getAmount() != null) return Math.abs(req.getAmount());
    if (req.getQuantity() != null && req.getPrice() != null) {
      return Math.abs(req.getQuantity() * req.getPrice());
    }
    return null;
  }

  private Double resolveCostPerUnit(TaxLotEntity entity, TaxLotRequest req) {
    if (req.getCostPerUnit() != null) return req.getCostPerUnit();
    Double basis = req.getCostBasis();
    Double qty = req.getQuantity();
    if (basis != null && qty != null && qty != 0.0) {
      return basis / qty;
    }
    return entity.getCostPerUnit();
  }

  private String normalizeSymbol(String symbol) {
    return symbol == null ? null : symbol.trim().toUpperCase(Locale.US);
  }

  private LedgerEntry toDto(LedgerEntryEntity entity) {
    LedgerEntry out = new LedgerEntry();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setAccountId(entity.getAccountId());
    out.setBrokerAccountId(entity.getBrokerAccountId());
    out.setEntryType(entity.getEntryType());
    out.setSymbol(entity.getSymbol());
    out.setInstrumentId(entity.getInstrumentId());
    out.setAssetClass(entity.getAssetClass());
    out.setQuantity(entity.getQuantity());
    out.setPrice(entity.getPrice());
    out.setAmount(entity.getAmount());
    out.setCurrency(entity.getCurrency());
    out.setFxRate(entity.getFxRate());
    out.setTradeDate(entity.getTradeDate());
    out.setSettleDate(entity.getSettleDate());
    out.setDescription(entity.getDescription());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private TaxLot toDto(TaxLotEntity entity) {
    TaxLot out = new TaxLot();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setAccountId(entity.getAccountId());
    out.setSymbol(entity.getSymbol());
    out.setInstrumentId(entity.getInstrumentId());
    out.setAssetClass(entity.getAssetClass());
    out.setQuantity(entity.getQuantity());
    out.setCostBasis(entity.getCostBasis());
    out.setCostPerUnit(entity.getCostPerUnit());
    out.setAcquiredAt(entity.getAcquiredAt());
    out.setDisposedAt(entity.getDisposedAt());
    out.setStatus(entity.getStatus());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private CorporateAction toDto(CorporateActionEntity entity) {
    CorporateAction out = new CorporateAction();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setAccountId(entity.getAccountId());
    out.setActionType(entity.getActionType());
    out.setSymbol(entity.getSymbol());
    out.setInstrumentId(entity.getInstrumentId());
    out.setRatio(entity.getRatio());
    out.setCashAmount(entity.getCashAmount());
    out.setEffectiveDate(entity.getEffectiveDate());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private Statement toDto(StatementEntity entity) {
    Statement out = new Statement();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setAccountId(entity.getAccountId());
    out.setPeriodStart(entity.getPeriodStart());
    out.setPeriodEnd(entity.getPeriodEnd());
    out.setBaseCurrency(entity.getBaseCurrency());
    out.setStartingBalance(entity.getStartingBalance());
    out.setEndingBalance(entity.getEndingBalance());
    out.setDeposits(entity.getDeposits());
    out.setWithdrawals(entity.getWithdrawals());
    out.setDividends(entity.getDividends());
    out.setFees(entity.getFees());
    out.setRealizedPnl(entity.getRealizedPnl());
    out.setUnrealizedPnl(entity.getUnrealizedPnl());
    out.setNetCashFlow(entity.getNetCashFlow());
    out.setTradeNotional(entity.getTradeNotional());
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

  private record StatementSummary(double startingBalance, double endingBalance, double deposits, double withdrawals,
                                  double dividends, double fees, double netCashFlow, double tradeNotional,
                                  int tradeCount) {
  }
}

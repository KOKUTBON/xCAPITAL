package com.alphamath.portfolio.application.reporting;

import com.alphamath.portfolio.domain.reporting.LedgerEntryRequest;
import com.alphamath.portfolio.domain.reporting.LedgerEntryType;
import com.alphamath.portfolio.domain.reporting.ReconciliationReport;
import com.alphamath.portfolio.domain.reporting.ReconciliationRequest;
import com.alphamath.portfolio.domain.reporting.StatementImportError;
import com.alphamath.portfolio.domain.reporting.StatementImportRequest;
import com.alphamath.portfolio.domain.reporting.StatementImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StatementImportService {
  private static final int BATCH_SIZE = 500;

  private final ReportingService reporting;
  private final ReconciliationService reconciliation;

  public StatementImportService(ReportingService reporting,
                                ReconciliationService reconciliation) {
    this.reporting = reporting;
    this.reconciliation = reconciliation;
  }

  public StatementImportResult importStatement(String userId, StatementImportRequest req) {
    if (req == null || req.getAccountId() == null || req.getAccountId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId required");
    }
    if (req.getCsv() == null || req.getCsv().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csv is required");
    }

    char delimiter = resolveDelimiter(req.getDelimiter());
    String source = req.getSource() == null || req.getSource().isBlank()
        ? "statement" : req.getSource().trim();
    String defaultCurrency = req.getDefaultCurrency() == null || req.getDefaultCurrency().isBlank()
        ? "USD" : req.getDefaultCurrency().trim();

    StatementImportResult result = new StatementImportResult();
    result.setAccountId(req.getAccountId());
    result.setSource(source);
    result.setImportedAt(Instant.now());

    List<LedgerEntryRequest> batch = new ArrayList<>(BATCH_SIZE);
    int lineNo = 0;
    int totalRows = 0;
    int ingestedRows = 0;
    List<StatementImportError> errors = new ArrayList<>();

    Map<String, Integer> headerMap = new LinkedHashMap<>();
    boolean headerRead = false;

    try (BufferedReader reader = new BufferedReader(new StringReader(req.getCsv()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lineNo++;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        if (req.isHasHeader() && !headerRead) {
          headerMap = parseHeader(trimmed, delimiter);
          headerRead = true;
          continue;
        }

        totalRows++;
        try {
          List<String> columns = parseCsvLine(trimmed, delimiter);
          LedgerEntryRequest entry = mapRow(columns, headerMap, req.getAccountId(), defaultCurrency, source);
          if (entry != null) {
            batch.add(entry);
          }
          if (batch.size() >= BATCH_SIZE) {
            ingestedRows += reporting.addLedgerEntries(userId, batch).size();
            batch.clear();
          }
        } catch (Exception e) {
          StatementImportError error = new StatementImportError();
          error.setLine(lineNo);
          error.setMessage(e.getMessage());
          errors.add(error);
        }
      }

      if (!batch.isEmpty()) {
        ingestedRows += reporting.addLedgerEntries(userId, batch).size();
      }
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csv parse failed");
    }

    result.setTotalRows(totalRows);
    result.setIngestedRows(ingestedRows);
    result.setFailedRows(errors.size());
    result.setErrors(errors);

    if (req.isApplyPositions() || req.isRebuildTaxLots()) {
      ReconciliationRequest reconcileReq = new ReconciliationRequest();
      reconcileReq.setAccountId(req.getAccountId());
      reconcileReq.setApplyPositions(req.isApplyPositions());
      reconcileReq.setRebuildTaxLots(req.isRebuildTaxLots());
      reconcileReq.setLotMethod(req.getLotMethod());
      ReconciliationReport report = reconciliation.reconcile(userId, reconcileReq);
      result.setReconciliation(report);
    }

    return result;
  }

  private LedgerEntryRequest mapRow(List<String> columns,
                                    Map<String, Integer> headerMap,
                                    String accountId,
                                    String defaultCurrency,
                                    String source) {
    boolean hasHeader = headerMap != null && !headerMap.isEmpty();
    String rawType = hasHeader
        ? value(columns, headerMap, "type", "action", "transaction", "transaction_type", "activity")
        : valueByIndex(columns, 1);
    String rawSymbol = hasHeader
        ? value(columns, headerMap, "symbol", "ticker", "security")
        : valueByIndex(columns, 2);
    String rawQty = hasHeader
        ? value(columns, headerMap, "quantity", "qty", "shares", "units")
        : valueByIndex(columns, 3);
    String rawPrice = hasHeader
        ? value(columns, headerMap, "price", "trade_price", "execution_price")
        : valueByIndex(columns, 4);
    String rawAmount = hasHeader
        ? value(columns, headerMap, "amount", "net_amount", "cash", "value")
        : valueByIndex(columns, 5);
    String rawCurrency = hasHeader
        ? value(columns, headerMap, "currency", "ccy")
        : valueByIndex(columns, 6);
    String rawDescription = hasHeader
        ? value(columns, headerMap, "description", "memo", "details", "note")
        : valueByIndex(columns, 7);
    String rawTimestamp = hasHeader
        ? value(columns, headerMap, "timestamp", "date", "trade_date", "trade_date_utc")
        : valueByIndex(columns, 0);

    Double qty = parseDouble(rawQty);
    Double price = parseDouble(rawPrice);
    Double amount = parseDouble(rawAmount);

    LedgerEntryType type = mapEntryType(rawType, qty, amount);
    if (qty != null && qty < 0) {
      qty = Math.abs(qty);
      if (type == LedgerEntryType.BUY || type == LedgerEntryType.SELL) {
        type = LedgerEntryType.SELL;
      }
    }

    if (amount == null && qty != null && price != null) {
      double signed = type == LedgerEntryType.SELL ? -1.0 : 1.0;
      amount = qty * price * signed;
    }

    LedgerEntryRequest entry = new LedgerEntryRequest();
    entry.setAccountId(accountId);
    entry.setEntryType(type.name());
    entry.setSymbol(rawSymbol == null || rawSymbol.isBlank() ? null : rawSymbol.trim());
    entry.setQuantity(qty);
    entry.setPrice(price);
    entry.setAmount(amount);
    entry.setCurrency(rawCurrency == null || rawCurrency.isBlank() ? defaultCurrency : rawCurrency.trim());
    entry.setDescription(rawDescription == null ? null : rawDescription.trim());
    entry.setTradeDate(parseTimestamp(rawTimestamp));
    entry.setMetadata(Map.of(
        "source", source,
        "rawType", rawType == null ? "" : rawType
    ));
    return entry;
  }

  private List<String> parseCsvLine(String line, char delimiter) {
    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
        continue;
      }
      if (c == delimiter && !inQuotes) {
        out.add(current.toString().trim());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    out.add(current.toString().trim());
    return out;
  }

  private Map<String, Integer> parseHeader(String line, char delimiter) {
    List<String> columns = parseCsvLine(line, delimiter);
    Map<String, Integer> out = new LinkedHashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      String normalized = normalizeHeader(columns.get(i));
      if (!normalized.isBlank()) {
        out.put(normalized, i);
      }
    }
    return out;
  }

  private String normalizeHeader(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim().toLowerCase(Locale.US);
    trimmed = trimmed.replace(" ", "_");
    trimmed = trimmed.replace("-", "_");
    trimmed = trimmed.replace("/", "_");
    return trimmed;
  }

  private String value(List<String> columns, Map<String, Integer> headerMap, String... keys) {
    if (columns == null || columns.isEmpty()) {
      return null;
    }
    if (headerMap != null && !headerMap.isEmpty()) {
      for (String key : keys) {
        Integer idx = headerMap.get(key);
        if (idx != null && idx >= 0 && idx < columns.size()) {
          return columns.get(idx);
        }
      }
    }
    return null;
  }

  private String valueByIndex(List<String> columns, int index) {
    if (columns == null || index < 0 || index >= columns.size()) {
      return null;
    }
    return columns.get(index);
  }

  private LedgerEntryType mapEntryType(String raw, Double qty, Double amount) {
    if (raw == null || raw.isBlank()) {
      if (qty != null && qty < 0) {
        return LedgerEntryType.SELL;
      }
      if (qty != null && qty > 0) {
        return LedgerEntryType.BUY;
      }
      return LedgerEntryType.ADJUSTMENT;
    }
    String value = raw.trim().toUpperCase(Locale.US);
    if (value.contains("BUY")) return LedgerEntryType.BUY;
    if (value.contains("SELL")) return LedgerEntryType.SELL;
    if (value.contains("DIV")) return LedgerEntryType.DIVIDEND;
    if (value.contains("FEE") || value.contains("COMM")) return LedgerEntryType.FEE;
    if (value.contains("INTEREST")) return LedgerEntryType.INTEREST;
    if (value.contains("WITHDRAW") || value.contains("DEBIT")) return LedgerEntryType.WITHDRAWAL;
    if (value.contains("DEPOSIT") || value.contains("CREDIT")) return LedgerEntryType.DEPOSIT;
    if (value.contains("TRANSFER")) return LedgerEntryType.TRANSFER;
    if (value.contains("FX") || value.contains("FOREX")) return LedgerEntryType.FX;
    if (qty != null && qty < 0) return LedgerEntryType.SELL;
    if (amount != null && amount < 0) return LedgerEntryType.WITHDRAWAL;
    if (amount != null && amount > 0) return LedgerEntryType.DEPOSIT;
    return LedgerEntryType.ADJUSTMENT;
  }

  private Instant parseTimestamp(String raw) {
    if (raw == null || raw.isBlank()) {
      return Instant.now();
    }
    String ts = raw.trim();
    try {
      return Instant.parse(ts);
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDate.parse(ts).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e) {
        return Instant.now();
      }
    }
  }

  private Double parseDouble(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    boolean negative = false;
    if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
      negative = true;
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    trimmed = trimmed.replace("$", "").replace(",", "");
    double value = Double.parseDouble(trimmed);
    return negative ? -value : value;
  }

  private char resolveDelimiter(String raw) {
    if (raw == null || raw.isBlank()) {
      return ',';
    }
    return raw.trim().charAt(0);
  }
}

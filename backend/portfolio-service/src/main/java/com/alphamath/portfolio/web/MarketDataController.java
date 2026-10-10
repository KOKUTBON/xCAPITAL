package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.marketdata.LatestQuotesResult;
import com.alphamath.portfolio.application.marketdata.MarketDataBackfillService;
import com.alphamath.portfolio.application.marketdata.MarketDataEntitlementService;
import com.alphamath.portfolio.application.marketdata.MarketDataLicenseCatalogService;
import com.alphamath.portfolio.application.marketdata.MarketDataProviderCatalog;
import com.alphamath.portfolio.application.marketdata.MarketDataService;
import com.alphamath.portfolio.domain.marketdata.MarketDataBackfillResult;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlement;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlementRequest;
import com.alphamath.portfolio.domain.marketdata.MarketDataLicense;
import com.alphamath.portfolio.domain.marketdata.MarketDataLicenseCatalogEntry;
import com.alphamath.portfolio.domain.marketdata.MarketDataLicenseRequest;
import com.alphamath.portfolio.domain.marketdata.MarketPrice;
import com.alphamath.portfolio.domain.marketdata.MarketPriceInput;
import com.alphamath.portfolio.domain.marketdata.PriceGranularity;
import com.alphamath.portfolio.infrastructure.persistence.MarketPriceEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {
  private final MarketDataService service;
  private final MarketDataBackfillService backfill;
  private final MarketDataEntitlementService entitlements;
  private final MarketDataLicenseCatalogService licenseCatalog;
  private final MarketDataProviderCatalog providers;
  private final SecurityGuard security;

  public MarketDataController(MarketDataService service,
                              MarketDataBackfillService backfill,
                              MarketDataEntitlementService entitlements,
                              MarketDataLicenseCatalogService licenseCatalog,
                              MarketDataProviderCatalog providers,
                              SecurityGuard security) {
    this.service = service;
    this.backfill = backfill;
    this.entitlements = entitlements;
    this.licenseCatalog = licenseCatalog;
    this.providers = providers;
    this.security = security;
  }

  @PostMapping("/prices")
  public IngestResponse ingest(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                               @Valid @RequestBody IngestRequest req) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    int count = service.ingest(req.source, req.prices);
    return new IngestResponse(count, "Ingested " + count + " price rows");
  }

  @GetMapping("/prices")
  public List<PriceResponse> prices(@RequestParam String symbol,
                                    @RequestParam(required = false) String start,
                                    @RequestParam(required = false) String end,
                                    @RequestParam(required = false, defaultValue = "0") int limit,
                                    Principal principal) {
    entitlements.assertSymbols(userId(principal), List.of(symbol));
    Instant startTs = parseOptional(start);
    Instant endTs = parseOptional(end);
    List<MarketPriceEntity> rows = service.listPrices(symbol, startTs, endTs, limit);
    return rows.stream().map(PriceResponse::fromEntity).toList();
  }

  @GetMapping("/symbols")
  public List<String> symbols(Principal principal) {
    return entitlements.filterSymbols(userId(principal), service.symbols());
  }

  @GetMapping("/providers")
  public List<MarketDataProviderCatalog.MarketDataProviderSnapshot> providers() {
    return providers.listProviders();
  }

  @GetMapping("/licenses")
  public List<MarketDataLicense> licenses(@RequestParam(required = false) String status,
                                          Principal principal) {
    return entitlements.listLicenses(userId(principal), status);
  }

  @GetMapping("/licenses/catalog")
  public List<MarketDataLicenseCatalogEntry> licenseCatalog() {
    return licenseCatalog.listCatalog();
  }

  @PostMapping("/licenses")
  public MarketDataLicense upsertLicense(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                                         @RequestBody MarketDataLicenseRequest req,
                                         Principal principal) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    return entitlements.upsertLicense(userId(principal), req);
  }

  @GetMapping("/entitlements")
  public List<MarketDataEntitlement> entitlements(@RequestParam(required = false) String status,
                                                  Principal principal) {
    return entitlements.listEntitlements(userId(principal), status);
  }

  @PostMapping("/entitlements")
  public MarketDataEntitlement upsertEntitlement(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                                                 @RequestBody MarketDataEntitlementRequest req,
                                                 Principal principal) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    return entitlements.upsertEntitlement(userId(principal), req);
  }

  @GetMapping("/quotes/latest")
  public LatestQuotesResponse latestQuotes(@RequestParam String symbols, Principal principal) {
    List<String> list = parseSymbols(symbols);
    entitlements.assertSymbols(userId(principal), list);
    LatestQuotesResult result = service.latestQuotes(list);
    LatestQuotesResponse out = new LatestQuotesResponse();
    out.asOf = Instant.now();
    out.requested = list.size();
    out.returned = result.quotes().size();
    out.cacheHits = result.cacheHits();
    out.fetched = result.fetched();
    out.missing = result.missing();
    out.quotes = result.quotes().stream()
        .map(QuoteResponse::fromSnapshot)
        .toList();
    return out;
  }

  @GetMapping("/history")
  public HistoryResponse history(@RequestParam String symbol,
                                 @RequestParam(required = false) String start,
                                 @RequestParam(required = false) String end,
                                 @RequestParam(required = false, defaultValue = "DAY") String granularity,
                                 @RequestParam(required = false, defaultValue = "0") int limit,
                                 Principal principal) {
    entitlements.assertSymbols(userId(principal), List.of(symbol));
    Instant startTs = parseOptional(start);
    Instant endTs = parseOptional(end);
    PriceGranularity g = parseGranularity(granularity);
    List<MarketPrice> data = service.historicalPrices(symbol, startTs, endTs, g, limit);
    HistoryResponse out = new HistoryResponse();
    out.symbol = symbol.trim().toUpperCase(Locale.US);
    out.granularity = g.name();
    out.start = startTs;
    out.end = endTs;
    out.points = data.size();
    out.prices = data.stream().map(PriceResponse::fromMarketPrice).toList();
    return out;
  }

  @GetMapping("/returns")
  public List<Double> returns(@RequestParam String symbol,
                              @RequestParam(required = false) String start,
                              @RequestParam(required = false) String end,
                              @RequestParam(required = false, defaultValue = "0") int limit,
                              Principal principal) {
    entitlements.assertSymbols(userId(principal), List.of(symbol));
    Instant startTs = parseOptional(start);
    Instant endTs = parseOptional(end);
    return service.returns(symbol, startTs, endTs, limit);
  }

  @PostMapping("/backfill")
  public MarketDataBackfillResult backfill(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                                           @Valid @RequestBody BackfillRequest req,
                                           Principal principal) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    List<String> symbols = parseSymbolList(req.symbols);
    entitlements.assertSymbols(userId(principal), symbols);
    Instant startTs = parseOptional(req.start);
    Instant endTs = parseOptional(req.end);
    PriceGranularity g = parseGranularity(req.granularity);
    return backfill.backfill(symbols, startTs, endTs, g, req.limit, req.source);
  }

  private Instant parseOptional(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String ts = raw.trim();
    try {
      return Instant.parse(ts);
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDate.parse(ts).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timestamp must be ISO-8601");
      }
    }
  }

  private List<String> parseSymbols(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
    }
    String[] parts = raw.split(",");
    List<String> out = new ArrayList<>();
    for (String part : parts) {
      if (part == null) continue;
      String symbol = part.trim();
      if (symbol.isEmpty()) continue;
      out.add(symbol);
    }
    if (out.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
    }
    return out;
  }

  private List<String> parseSymbolList(List<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
    }
    List<String> out = new ArrayList<>();
    for (String symbol : symbols) {
      if (symbol == null) continue;
      String trimmed = symbol.trim();
      if (trimmed.isEmpty()) continue;
      out.add(trimmed);
    }
    if (out.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
    }
    return out;
  }

  private PriceGranularity parseGranularity(String raw) {
    if (raw == null || raw.isBlank()) {
      return PriceGranularity.DAY;
    }
    try {
      return PriceGranularity.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "granularity must be MINUTE, HOUR, or DAY");
    }
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  @Data
  public static class IngestRequest {
    public String source = "manual";

    @NotNull @Size(min = 1, max = 50000)
    public List<@Valid MarketPriceInput> prices;
  }

  @Data
  public static class IngestResponse {
    public final int ingested;
    public final String message;
  }

  @Data
  public static class BackfillRequest {
    public List<String> symbols = new ArrayList<>();
    public String start;
    public String end;
    public String granularity = "DAY";
    public int limit = 0;
    public String source = "csv";
  }

  @Data
  public static class PriceResponse {
    public String symbol;
    public Instant timestamp;
    public double open;
    public double high;
    public double low;
    public double close;
    public Double volume;
    public String source;

    static PriceResponse fromEntity(MarketPriceEntity entity) {
      PriceResponse out = new PriceResponse();
      out.symbol = entity.getSymbol();
      out.timestamp = entity.getTs();
      out.open = entity.getOpen();
      out.high = entity.getHigh();
      out.low = entity.getLow();
      out.close = entity.getClose();
      out.volume = entity.getVolume();
      out.source = entity.getSource();
      return out;
    }

    static PriceResponse fromMarketPrice(MarketPrice price) {
      PriceResponse out = new PriceResponse();
      out.symbol = price.symbol();
      out.timestamp = price.timestamp();
      out.open = price.open();
      out.high = price.high();
      out.low = price.low();
      out.close = price.close();
      out.volume = price.volume();
      out.source = price.source();
      return out;
    }
  }

  @Data
  public static class QuoteResponse {
    public String symbol;
    public Instant timestamp;
    public double price;
    public String source;
    public boolean cacheHit;

    static QuoteResponse fromSnapshot(LatestQuotesResult.QuoteSnapshot snapshot) {
      QuoteResponse out = new QuoteResponse();
      out.symbol = snapshot.quote().symbol();
      out.timestamp = snapshot.quote().timestamp();
      out.price = snapshot.quote().price();
      out.source = snapshot.quote().source();
      out.cacheHit = snapshot.cacheHit();
      return out;
    }
  }

  @Data
  public static class LatestQuotesResponse {
    public Instant asOf;
    public int requested;
    public int returned;
    public int cacheHits;
    public int fetched;
    public List<String> missing = new ArrayList<>();
    public List<QuoteResponse> quotes = new ArrayList<>();
  }

  @Data
  public static class HistoryResponse {
    public String symbol;
    public String granularity;
    public Instant start;
    public Instant end;
    public int points;
    public List<PriceResponse> prices = new ArrayList<>();
  }
}

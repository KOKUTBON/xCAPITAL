package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.reference.ExchangeCalendarSyncService;
import com.alphamath.portfolio.application.reference.ReferenceDataService;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reference.Currency;
import com.alphamath.portfolio.domain.reference.ExchangeCalendarDay;
import com.alphamath.portfolio.domain.reference.ExchangeSessionStatus;
import com.alphamath.portfolio.domain.reference.ExchangeCalendarSyncRequest;
import com.alphamath.portfolio.domain.reference.Exchange;
import com.alphamath.portfolio.domain.reference.FxRate;
import com.alphamath.portfolio.domain.reference.Instrument;
import com.alphamath.portfolio.domain.reference.InstrumentStatus;
import com.alphamath.portfolio.domain.reference.InstrumentType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reference")
public class ReferenceDataController {
  private final ReferenceDataService reference;
  private final SecurityGuard security;
  private final ExchangeCalendarSyncService calendarSync;

  public ReferenceDataController(ReferenceDataService reference,
                                 SecurityGuard security,
                                 ExchangeCalendarSyncService calendarSync) {
    this.reference = reference;
    this.security = security;
    this.calendarSync = calendarSync;
  }

  @PostMapping("/instruments")
  public List<Instrument> upsertInstruments(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                                            @RequestBody List<InstrumentRequest> reqs) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    List<Instrument> inputs = new ArrayList<>();
    for (InstrumentRequest req : reqs) {
      Instrument instrument = new Instrument();
      instrument.setId(req.id);
      instrument.setSymbol(req.symbol);
      instrument.setName(req.name);
      instrument.setAssetClass(parseAssetClass(req.assetClass));
      instrument.setInstrumentType(parseInstrumentType(req.instrumentType));
      instrument.setExchangeCode(req.exchangeCode);
      instrument.setCurrency(req.currency);
      instrument.setStatus(parseInstrumentStatus(req.status));
      instrument.setExternalIds(req.externalIds == null ? new LinkedHashMap<>() : req.externalIds);
      instrument.setMetadata(req.metadata == null ? new LinkedHashMap<>() : req.metadata);
      inputs.add(instrument);
    }
    return reference.upsertInstruments(inputs);
  }

  @GetMapping("/instruments")
  public List<Instrument> listInstruments(@RequestParam(required = false) String symbol,
                                          @RequestParam(required = false) String assetClass,
                                          @RequestParam(required = false) String instrumentType,
                                          @RequestParam(required = false) Integer limit) {
    return reference.listInstruments(symbol, assetClass, instrumentType, limit == null ? 0 : limit);
  }

  @GetMapping("/instruments/{id}")
  public Instrument getInstrument(@PathVariable String id) {
    return reference.getInstrument(id);
  }

  @PostMapping("/exchanges")
  public List<Exchange> upsertExchanges(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                                        @RequestBody List<ExchangeRequest> reqs) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    List<Exchange> inputs = new ArrayList<>();
    for (ExchangeRequest req : reqs) {
      Exchange ex = new Exchange();
      ex.setCode(req.code);
      ex.setName(req.name);
      ex.setRegion(req.region == null ? null : parseRegion(req.region));
      ex.setTimezone(req.timezone);
      ex.setMic(req.mic);
      ex.setCurrency(req.currency);
      ex.setOpenTime(req.openTime);
      ex.setCloseTime(req.closeTime);
      inputs.add(ex);
    }
    return reference.upsertExchanges(inputs);
  }

  @GetMapping("/exchanges")
  public List<Exchange> listExchanges() {
    return reference.listExchanges();
  }

  @PostMapping("/exchanges/{code}/calendar")
  public List<ExchangeCalendarDay> upsertCalendar(@PathVariable String code,
                                                  @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                                  @RequestBody List<ExchangeCalendarRequest> reqs) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    List<ExchangeCalendarDay> inputs = new ArrayList<>();
    for (ExchangeCalendarRequest req : reqs) {
      ExchangeCalendarDay day = new ExchangeCalendarDay();
      day.setExchangeCode(code);
      day.setSessionDate(parseDate(req.sessionDate));
      day.setStatus(parseSessionStatus(req.status));
      day.setOpenTime(req.openTime);
      day.setCloseTime(req.closeTime);
      day.setNotes(req.notes);
      inputs.add(day);
    }
    return reference.upsertExchangeCalendar(code, inputs);
  }

  @PostMapping("/exchanges/{code}/calendar/sync")
  public List<ExchangeCalendarDay> syncCalendar(@PathVariable String code,
                                                @RequestHeader(value = "X-User-Roles", required = false) String roles,
                                                @RequestBody ExchangeCalendarSyncRequest req) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    LocalDate start = req.getStart() == null ? null : req.getStart();
    LocalDate end = req.getEnd() == null ? null : req.getEnd();
    return calendarSync.sync(code, req.getProviderId(), start, end, req.getMetadata());
  }

  @GetMapping("/calendar/providers")
  public List<String> calendarProviders() {
    return calendarSync.listProviders();
  }

  @GetMapping("/exchanges/{code}/calendar")
  public List<ExchangeCalendarDay> listCalendar(@PathVariable String code,
                                                @RequestParam(required = false) String start,
                                                @RequestParam(required = false) String end) {
    return reference.listExchangeCalendar(code, parseDate(start), parseDate(end));
  }

  @GetMapping("/exchanges/{code}/calendar/next-open")
  public ExchangeCalendarDay nextOpen(@PathVariable String code,
                                      @RequestParam(required = false) String date) {
    ExchangeCalendarDay day = reference.nextOpenSession(code, parseDate(date));
    if (day == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No open session found");
    }
    return day;
  }

  @PostMapping("/currencies")
  public List<Currency> upsertCurrencies(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                                         @RequestBody List<CurrencyRequest> reqs) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    List<Currency> inputs = new ArrayList<>();
    for (CurrencyRequest req : reqs) {
      Currency currency = new Currency();
      currency.setCode(req.code);
      currency.setName(req.name);
      currency.setSymbol(req.symbol);
      currency.setDecimals(req.decimals == null ? 2 : req.decimals);
      inputs.add(currency);
    }
    return reference.upsertCurrencies(inputs);
  }

  @GetMapping("/currencies")
  public List<Currency> listCurrencies() {
    return reference.listCurrencies();
  }

  @PostMapping("/fx-rates")
  public List<FxRate> addFxRates(@RequestHeader(value = "X-User-Roles", required = false) String roles,
                                 @RequestBody List<FxRateRequest> reqs) {
    security.requireRole(roles, "ADMIN", "DATA_ADMIN");
    List<FxRate> inputs = new ArrayList<>();
    for (FxRateRequest req : reqs) {
      FxRate rate = new FxRate();
      rate.setBaseCcy(req.baseCcy);
      rate.setQuoteCcy(req.quoteCcy);
      rate.setRate(req.rate);
      rate.setTimestamp(req.timestamp == null ? Instant.now() : req.timestamp);
      rate.setSource(req.source);
      inputs.add(rate);
    }
    return reference.addFxRates(inputs);
  }

  @GetMapping("/fx-rates")
  public List<FxRate> latestFxRates(@RequestParam String base,
                                    @RequestParam String quote,
                                    @RequestParam(required = false) Integer limit) {
    return reference.latestFxRates(base, quote, limit == null ? 0 : limit);
  }

  private AssetClass parseAssetClass(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return AssetClass.valueOf(raw.trim().toUpperCase());
  }

  private InstrumentType parseInstrumentType(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return InstrumentType.valueOf(raw.trim().toUpperCase());
  }

  private InstrumentStatus parseInstrumentStatus(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return InstrumentStatus.valueOf(raw.trim().toUpperCase());
  }

  private com.alphamath.portfolio.domain.execution.Region parseRegion(String raw) {
    return com.alphamath.portfolio.domain.execution.Region.valueOf(raw.trim().toUpperCase());
  }

  private LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must be YYYY-MM-DD");
    }
  }

  private ExchangeSessionStatus parseSessionStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return ExchangeSessionStatus.OPEN;
    }
    try {
      return ExchangeSessionStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid session status");
    }
  }

  public static class InstrumentRequest {
    public String id;
    public String symbol;
    public String name;
    public String assetClass;
    public String instrumentType;
    public String exchangeCode;
    public String currency;
    public String status;
    public Map<String, String> externalIds;
    public Map<String, Object> metadata;
  }

  public static class ExchangeRequest {
    public String code;
    public String name;
    public String region;
    public String timezone;
    public String mic;
    public String currency;
    public String openTime;
    public String closeTime;
  }

  public static class ExchangeCalendarRequest {
    public String sessionDate;
    public String status;
    public String openTime;
    public String closeTime;
    public String notes;
  }

  public static class CurrencyRequest {
    public String code;
    public String name;
    public String symbol;
    public Integer decimals;
  }

  public static class FxRateRequest {
    public String baseCcy;
    public String quoteCcy;
    public double rate;
    public Instant timestamp;
    public String source;
  }
}

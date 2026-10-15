package com.alphamath.portfolio.application.reference;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.reference.Currency;
import com.alphamath.portfolio.domain.reference.ExchangeCalendarDay;
import com.alphamath.portfolio.domain.reference.ExchangeSessionStatus;
import com.alphamath.portfolio.domain.reference.Exchange;
import com.alphamath.portfolio.domain.reference.FxRate;
import com.alphamath.portfolio.domain.reference.Instrument;
import com.alphamath.portfolio.domain.reference.InstrumentStatus;
import com.alphamath.portfolio.domain.reference.InstrumentType;
import com.alphamath.portfolio.infrastructure.persistence.ExchangeCalendarEntity;
import com.alphamath.portfolio.infrastructure.persistence.ExchangeCalendarRepository;
import com.alphamath.portfolio.infrastructure.persistence.CurrencyEntity;
import com.alphamath.portfolio.infrastructure.persistence.CurrencyRepository;
import com.alphamath.portfolio.infrastructure.persistence.ExchangeEntity;
import com.alphamath.portfolio.infrastructure.persistence.ExchangeRepository;
import com.alphamath.portfolio.infrastructure.persistence.FxRateEntity;
import com.alphamath.portfolio.infrastructure.persistence.FxRateRepository;
import com.alphamath.portfolio.infrastructure.persistence.InstrumentEntity;
import com.alphamath.portfolio.infrastructure.persistence.InstrumentRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class ReferenceDataService {
  private final InstrumentRepository instruments;
  private final ExchangeRepository exchanges;
  private final ExchangeCalendarRepository calendars;
  private final CurrencyRepository currencies;
  private final FxRateRepository fxRates;

  public ReferenceDataService(InstrumentRepository instruments,
                              ExchangeRepository exchanges,
                              ExchangeCalendarRepository calendars,
                              CurrencyRepository currencies,
                              FxRateRepository fxRates) {
    this.instruments = instruments;
    this.exchanges = exchanges;
    this.calendars = calendars;
    this.currencies = currencies;
    this.fxRates = fxRates;
  }

  public List<Instrument> upsertInstruments(List<Instrument> inputs) {
    Instant now = Instant.now();
    List<Instrument> out = new ArrayList<>();
    for (Instrument input : inputs) {
      InstrumentEntity existing = input.getId() == null ? null : instruments.findById(input.getId()).orElse(null);
      InstrumentEntity entity = existing == null ? new InstrumentEntity() : existing;
      String id = existing == null ? UUID.randomUUID().toString() : entity.getId();

      entity.setId(id);
      entity.setSymbol(input.getSymbol());
      entity.setName(input.getName());
      entity.setAssetClass(input.getAssetClass() == null ? AssetClass.EQUITY : input.getAssetClass());
      entity.setInstrumentType(input.getInstrumentType() == null ? InstrumentType.STOCK : input.getInstrumentType());
      entity.setExchangeCode(input.getExchangeCode());
      entity.setCurrency(input.getCurrency());
      entity.setStatus(input.getStatus() == null ? InstrumentStatus.ACTIVE : input.getStatus());
      entity.setExternalIdsJson(JsonUtils.toJson(input.getExternalIds() == null ? new LinkedHashMap<>() : input.getExternalIds()));
      entity.setMetadataJson(JsonUtils.toJson(input.getMetadata() == null ? new LinkedHashMap<>() : input.getMetadata()));
      entity.setCreatedAt(existing == null ? now : entity.getCreatedAt());
      entity.setUpdatedAt(now);

      instruments.save(entity);
      out.add(toDto(entity));
    }
    return out;
  }

  public List<Instrument> listInstruments(String symbol, String assetClass, String instrumentType, int limit) {
    List<InstrumentEntity> rows;
    if (symbol != null && !symbol.isBlank()) {
      rows = instruments.findBySymbolContainingIgnoreCase(symbol.trim());
    } else {
      rows = instruments.findAll();
    }

    List<Instrument> out = new ArrayList<>();
    for (InstrumentEntity row : rows) {
      if (assetClass != null && !assetClass.isBlank() && row.getAssetClass() != null
          && !row.getAssetClass().name().equalsIgnoreCase(assetClass)) {
        continue;
      }
      if (instrumentType != null && !instrumentType.isBlank() && row.getInstrumentType() != null
          && !row.getInstrumentType().name().equalsIgnoreCase(instrumentType)) {
        continue;
      }
      out.add(toDto(row));
      if (limit > 0 && out.size() >= limit) break;
    }
    return out;
  }

  public Instrument getInstrument(String id) {
    InstrumentEntity entity = instruments.findById(id).orElse(null);
    return entity == null ? null : toDto(entity);
  }

  public List<Exchange> upsertExchanges(List<Exchange> inputs) {
    Instant now = Instant.now();
    List<Exchange> out = new ArrayList<>();
    for (Exchange input : inputs) {
      ExchangeEntity entity = exchanges.findById(input.getCode()).orElse(new ExchangeEntity());
      entity.setCode(input.getCode());
      entity.setName(input.getName());
      entity.setRegion(input.getRegion());
      entity.setTimezone(input.getTimezone());
      entity.setMic(input.getMic());
      entity.setCurrency(input.getCurrency());
      entity.setOpenTime(input.getOpenTime());
      entity.setCloseTime(input.getCloseTime());
      entity.setCreatedAt(entity.getCreatedAt() == null ? now : entity.getCreatedAt());
      entity.setUpdatedAt(now);
      exchanges.save(entity);
      out.add(toDto(entity));
    }
    return out;
  }

  public List<Exchange> listExchanges() {
    List<Exchange> out = new ArrayList<>();
    for (ExchangeEntity entity : exchanges.findAll()) {
      out.add(toDto(entity));
    }
    return out;
  }

  public List<ExchangeCalendarDay> upsertExchangeCalendar(String exchangeCode, List<ExchangeCalendarDay> inputs) {
    ExchangeEntity exchange = exchanges.findById(exchangeCode).orElse(null);
    if (exchange == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exchange not found");
    }
    Instant now = Instant.now();
    List<ExchangeCalendarDay> out = new ArrayList<>();
    for (ExchangeCalendarDay input : inputs) {
      if (input.getSessionDate() == null) {
        continue;
      }
      String id = exchangeCode.toUpperCase() + "|" + input.getSessionDate();
      ExchangeCalendarEntity entity = calendars.findById(id).orElse(new ExchangeCalendarEntity());
      entity.setId(id);
      entity.setExchangeCode(exchangeCode.toUpperCase());
      entity.setSessionDate(input.getSessionDate());
      ExchangeSessionStatus status = input.getStatus() == null ? ExchangeSessionStatus.OPEN : input.getStatus();
      entity.setStatus(status.name());
      entity.setOpenTime(input.getOpenTime() == null ? exchange.getOpenTime() : input.getOpenTime());
      entity.setCloseTime(input.getCloseTime() == null ? exchange.getCloseTime() : input.getCloseTime());
      entity.setNotes(input.getNotes());
      entity.setCreatedAt(entity.getCreatedAt() == null ? now : entity.getCreatedAt());
      entity.setUpdatedAt(now);
      calendars.save(entity);
      out.add(toCalendarDto(entity));
    }
    return out;
  }

  public List<ExchangeCalendarDay> listExchangeCalendar(String exchangeCode, LocalDate start, LocalDate end) {
    List<ExchangeCalendarEntity> rows;
    if (start == null && end == null) {
      rows = calendars.findByExchangeCodeOrderBySessionDateAsc(exchangeCode.toUpperCase());
    } else {
      LocalDate startDate = start == null ? LocalDate.of(1970, 1, 1) : start;
      LocalDate endDate = end == null ? LocalDate.of(2100, 1, 1) : end;
      rows = calendars.findByExchangeCodeAndSessionDateBetweenOrderBySessionDateAsc(
          exchangeCode.toUpperCase(), startDate, endDate);
    }
    List<ExchangeCalendarDay> out = new ArrayList<>();
    for (ExchangeCalendarEntity row : rows) {
      out.add(toCalendarDto(row));
    }
    return out;
  }

  public ExchangeCalendarDay nextOpenSession(String exchangeCode, LocalDate date) {
    LocalDate start = date == null ? LocalDate.now() : date;
    ExchangeCalendarEntity row = calendars
        .findFirstByExchangeCodeAndSessionDateGreaterThanEqualAndStatusOrderBySessionDateAsc(
            exchangeCode.toUpperCase(), start, ExchangeSessionStatus.OPEN.name())
        .orElse(null);
    return row == null ? null : toCalendarDto(row);
  }

  public List<Currency> upsertCurrencies(List<Currency> inputs) {
    Instant now = Instant.now();
    List<Currency> out = new ArrayList<>();
    for (Currency input : inputs) {
      CurrencyEntity entity = currencies.findById(input.getCode()).orElse(new CurrencyEntity());
      entity.setCode(input.getCode());
      entity.setName(input.getName());
      entity.setSymbol(input.getSymbol());
      entity.setDecimals(input.getDecimals());
      entity.setCreatedAt(entity.getCreatedAt() == null ? now : entity.getCreatedAt());
      currencies.save(entity);
      out.add(toDto(entity));
    }
    return out;
  }

  public List<Currency> listCurrencies() {
    List<Currency> out = new ArrayList<>();
    for (CurrencyEntity entity : currencies.findAll()) {
      out.add(toDto(entity));
    }
    return out;
  }

  public List<FxRate> addFxRates(List<FxRate> inputs) {
    Instant now = Instant.now();
    List<FxRate> out = new ArrayList<>();
    for (FxRate input : inputs) {
      FxRateEntity entity = new FxRateEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setBaseCcy(input.getBaseCcy());
      entity.setQuoteCcy(input.getQuoteCcy());
      entity.setRate(input.getRate());
      entity.setTs(input.getTimestamp());
      entity.setSource(input.getSource() == null ? "manual" : input.getSource());
      entity.setCreatedAt(now);
      fxRates.save(entity);
      out.add(toDto(entity));
    }
    return out;
  }

  public List<FxRate> latestFxRates(String baseCcy, String quoteCcy, int limit) {
    List<FxRateEntity> rows = fxRates.findByBaseCcyAndQuoteCcyOrderByTsDesc(baseCcy, quoteCcy);
    List<FxRate> out = new ArrayList<>();
    for (FxRateEntity row : rows) {
      out.add(toDto(row));
      if (limit > 0 && out.size() >= limit) break;
    }
    return out;
  }

  private Instrument toDto(InstrumentEntity entity) {
    Instrument out = new Instrument();
    out.setId(entity.getId());
    out.setSymbol(entity.getSymbol());
    out.setName(entity.getName());
    out.setAssetClass(entity.getAssetClass());
    out.setInstrumentType(entity.getInstrumentType());
    out.setExchangeCode(entity.getExchangeCode());
    out.setCurrency(entity.getCurrency());
    out.setStatus(entity.getStatus());
    out.setExternalIds(parseExternalIds(entity.getExternalIdsJson()));
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private Exchange toDto(ExchangeEntity entity) {
    Exchange out = new Exchange();
    out.setCode(entity.getCode());
    out.setName(entity.getName());
    out.setRegion(entity.getRegion());
    out.setTimezone(entity.getTimezone());
    out.setMic(entity.getMic());
    out.setCurrency(entity.getCurrency());
    out.setOpenTime(entity.getOpenTime());
    out.setCloseTime(entity.getCloseTime());
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private ExchangeCalendarDay toCalendarDto(ExchangeCalendarEntity entity) {
    ExchangeCalendarDay out = new ExchangeCalendarDay();
    out.setExchangeCode(entity.getExchangeCode());
    out.setSessionDate(entity.getSessionDate());
    out.setStatus(ExchangeSessionStatus.valueOf(entity.getStatus()));
    out.setOpenTime(entity.getOpenTime());
    out.setCloseTime(entity.getCloseTime());
    out.setNotes(entity.getNotes());
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private Currency toDto(CurrencyEntity entity) {
    Currency out = new Currency();
    out.setCode(entity.getCode());
    out.setName(entity.getName());
    out.setSymbol(entity.getSymbol());
    out.setDecimals(entity.getDecimals());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private FxRate toDto(FxRateEntity entity) {
    FxRate out = new FxRate();
    out.setId(entity.getId());
    out.setBaseCcy(entity.getBaseCcy());
    out.setQuoteCcy(entity.getQuoteCcy());
    out.setRate(entity.getRate());
    out.setTimestamp(entity.getTs());
    out.setSource(entity.getSource());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private LinkedHashMap<String, String> parseExternalIds(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<LinkedHashMap<String, String>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  private LinkedHashMap<String, Object> parseMetadata(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }
}

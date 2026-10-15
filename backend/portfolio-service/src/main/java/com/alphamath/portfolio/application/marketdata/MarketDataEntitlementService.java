package com.alphamath.portfolio.application.marketdata;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlement;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlementRequest;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlementStatus;
import com.alphamath.portfolio.domain.marketdata.MarketDataEntitlementType;
import com.alphamath.portfolio.domain.marketdata.MarketDataLicense;
import com.alphamath.portfolio.domain.marketdata.MarketDataLicenseRequest;
import com.alphamath.portfolio.domain.marketdata.MarketDataLicenseStatus;
import com.alphamath.portfolio.infrastructure.persistence.ExchangeEntity;
import com.alphamath.portfolio.infrastructure.persistence.ExchangeRepository;
import com.alphamath.portfolio.infrastructure.persistence.InstrumentEntity;
import com.alphamath.portfolio.infrastructure.persistence.InstrumentRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataEntitlementEntity;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataEntitlementRepository;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataLicenseEntity;
import com.alphamath.portfolio.infrastructure.persistence.MarketDataLicenseRepository;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketDataEntitlementService {
  private final MarketDataLicenseRepository licenses;
  private final MarketDataEntitlementRepository entitlements;
  private final InstrumentRepository instruments;
  private final ExchangeRepository exchanges;
  private final MarketDataEntitlementProperties properties;
  private final TenantContext tenantContext;

  public MarketDataEntitlementService(MarketDataLicenseRepository licenses,
                                      MarketDataEntitlementRepository entitlements,
                                      InstrumentRepository instruments,
                                      ExchangeRepository exchanges,
                                      MarketDataEntitlementProperties properties,
                                      TenantContext tenantContext) {
    this.licenses = licenses;
    this.entitlements = entitlements;
    this.instruments = instruments;
    this.exchanges = exchanges;
    this.properties = properties;
    this.tenantContext = tenantContext;
  }

  public boolean enforcementEnabled() {
    return properties.isEnabled();
  }

  public List<MarketDataLicense> listLicenses(String userId, String status) {
    String orgId = tenantContext.getOrgId();
    List<MarketDataLicenseEntity> rows;
    if (status != null && !status.isBlank()) {
      rows = orgId == null
          ? licenses.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.trim().toUpperCase(Locale.US))
          : licenses.findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(userId, orgId, status.trim().toUpperCase(Locale.US));
    } else {
      rows = orgId == null
          ? licenses.findByUserIdOrderByCreatedAtDesc(userId)
          : licenses.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    }
    return rows.stream().map(this::toDto).toList();
  }

  public MarketDataLicense upsertLicense(String userId, MarketDataLicenseRequest req) {
    if (req == null || req.getProvider() == null || req.getProvider().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provider is required");
    }
    MarketDataLicenseEntity entity = null;
    String orgId = tenantContext.getOrgId();
    if (req.getId() != null && !req.getId().isBlank()) {
      entity = licenses.findById(req.getId()).orElse(null);
      if (entity != null && (!userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId())))) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "license not found");
      }
    }
    if (entity == null) {
      entity = new MarketDataLicenseEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setOrgId(orgId);
      entity.setCreatedAt(Instant.now());
    }
    entity.setProvider(req.getProvider().trim());
    entity.setStatus(parseLicenseStatus(req.getStatus()).name());
    entity.setPlan(req.getPlan());
    entity.setAssetClassesJson(JsonUtils.toJson(normalizeList(req.getAssetClasses())));
    entity.setExchangesJson(JsonUtils.toJson(normalizeList(req.getExchanges())));
    entity.setRegionsJson(JsonUtils.toJson(normalizeList(req.getRegions())));
    entity.setStartsAt(req.getStartsAt());
    entity.setEndsAt(req.getEndsAt());
    entity.setMetadataJson(JsonUtils.toJson(req.getMetadata() == null ? Map.of() : req.getMetadata()));
    entity.setUpdatedAt(Instant.now());
    licenses.save(entity);
    return toDto(entity);
  }

  public List<MarketDataEntitlement> listEntitlements(String userId, String status) {
    String orgId = tenantContext.getOrgId();
    List<MarketDataEntitlementEntity> rows;
    if (status != null && !status.isBlank()) {
      rows = orgId == null
          ? entitlements.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.trim().toUpperCase(Locale.US))
          : entitlements.findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(userId, orgId, status.trim().toUpperCase(Locale.US));
    } else {
      rows = orgId == null
          ? entitlements.findByUserIdOrderByCreatedAtDesc(userId)
          : entitlements.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    }
    return rows.stream().map(this::toDto).toList();
  }

  public MarketDataEntitlement upsertEntitlement(String userId, MarketDataEntitlementRequest req) {
    if (req == null || req.getEntitlementType() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entitlementType is required");
    }
    MarketDataEntitlementEntity entity = null;
    String orgId = tenantContext.getOrgId();
    if (req.getId() != null && !req.getId().isBlank()) {
      entity = entitlements.findById(req.getId()).orElse(null);
      if (entity != null && (!userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId())))) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "entitlement not found");
      }
    }
    if (entity == null) {
      entity = new MarketDataEntitlementEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setOrgId(orgId);
      entity.setCreatedAt(Instant.now());
    }
    entity.setEntitlementType(req.getEntitlementType().name());
    entity.setEntitlementValue(normalizeValue(req.getEntitlementValue()));
    entity.setStatus(parseEntitlementStatus(req.getStatus()).name());
    entity.setSource(req.getSource());
    entity.setUpdatedAt(Instant.now());
    entitlements.save(entity);
    return toDto(entity);
  }

  public void assertSymbols(String userId, List<String> symbols) {
    if (!properties.isEnabled()) {
      return;
    }
    if (userId == null || userId.isBlank() || "unknown".equalsIgnoreCase(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "market data entitlements required");
    }
    List<String> normalized = normalizeList(symbols);
    if (normalized.isEmpty()) {
      return;
    }
    EntitlementSets sets = loadEntitlements(userId);
    if (sets.global) {
      return;
    }

    List<String> denied = new ArrayList<>();
    for (String symbol : normalized) {
      if (sets.symbols.contains(symbol)) {
        continue;
      }
      InstrumentEntity instrument = instruments.findFirstBySymbolIgnoreCase(symbol);
      if (instrument != null) {
        if (instrument.getAssetClass() != null && sets.assetClasses.contains(instrument.getAssetClass().name())) {
          continue;
        }
        if (instrument.getExchangeCode() != null && sets.exchanges.contains(instrument.getExchangeCode())) {
          continue;
        }
        if (instrument.getExchangeCode() != null && sets.regions.size() > 0) {
          ExchangeEntity exchange = exchanges.findById(instrument.getExchangeCode()).orElse(null);
          if (exchange != null && exchange.getRegion() != null && sets.regions.contains(exchange.getRegion().name())) {
            continue;
          }
        }
      }
      denied.add(symbol);
    }

    if (!denied.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Market data entitlement missing for: " + String.join(",", denied));
    }
  }

  public List<String> filterSymbols(String userId, List<String> symbols) {
    if (!properties.isEnabled()) {
      return symbols == null ? List.of() : symbols;
    }
    if (symbols == null || symbols.isEmpty()) {
      return List.of();
    }
    EntitlementSets sets = loadEntitlements(userId);
    if (sets.global) {
      return symbols;
    }
    List<String> allowed = new ArrayList<>();
    for (String symbol : symbols) {
      String normalized = normalizeValue(symbol);
      if (normalized.isEmpty()) continue;
      if (sets.symbols.contains(normalized)) {
        allowed.add(normalized);
        continue;
      }
      InstrumentEntity instrument = instruments.findFirstBySymbolIgnoreCase(normalized);
      if (instrument != null) {
        if (instrument.getAssetClass() != null && sets.assetClasses.contains(instrument.getAssetClass().name())) {
          allowed.add(normalized);
          continue;
        }
        if (instrument.getExchangeCode() != null && sets.exchanges.contains(instrument.getExchangeCode())) {
          allowed.add(normalized);
          continue;
        }
        if (instrument.getExchangeCode() != null && !sets.regions.isEmpty()) {
          ExchangeEntity exchange = exchanges.findById(instrument.getExchangeCode()).orElse(null);
          if (exchange != null && exchange.getRegion() != null && sets.regions.contains(exchange.getRegion().name())) {
            allowed.add(normalized);
          }
        }
      }
    }
    return allowed;
  }

  private EntitlementSets loadEntitlements(String userId) {
    String orgId = tenantContext.getOrgId();
    List<MarketDataEntitlementEntity> rows = orgId == null
        ? entitlements.findByUserIdAndStatusOrderByCreatedAtDesc(userId, MarketDataEntitlementStatus.ACTIVE.name())
        : entitlements.findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(
            userId, orgId, MarketDataEntitlementStatus.ACTIVE.name());
    EntitlementSets sets = new EntitlementSets();
    for (MarketDataEntitlementEntity row : rows) {
      MarketDataEntitlementType type = parseEntitlementType(row.getEntitlementType());
      String value = normalizeValue(row.getEntitlementValue());
      switch (type) {
        case GLOBAL -> sets.global = true;
        case SYMBOL -> {
          if (!value.isEmpty()) sets.symbols.add(value);
        }
        case EXCHANGE -> {
          if (!value.isEmpty()) sets.exchanges.add(value);
        }
        case ASSET_CLASS -> {
          if (!value.isEmpty()) sets.assetClasses.add(value);
        }
        case REGION -> {
          if (!value.isEmpty()) sets.regions.add(value);
        }
      }
    }
    return sets;
  }

  private MarketDataLicense toDto(MarketDataLicenseEntity entity) {
    MarketDataLicense out = new MarketDataLicense();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setProvider(entity.getProvider());
    out.setStatus(parseLicenseStatus(entity.getStatus()));
    out.setPlan(entity.getPlan());
    out.setAssetClasses(parseList(entity.getAssetClassesJson()));
    out.setExchanges(parseList(entity.getExchangesJson()));
    out.setRegions(parseList(entity.getRegionsJson()));
    out.setStartsAt(entity.getStartsAt());
    out.setEndsAt(entity.getEndsAt());
    out.setMetadata(parseMetadata(entity.getMetadataJson()));
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private MarketDataEntitlement toDto(MarketDataEntitlementEntity entity) {
    MarketDataEntitlement out = new MarketDataEntitlement();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setEntitlementType(parseEntitlementType(entity.getEntitlementType()));
    out.setEntitlementValue(entity.getEntitlementValue());
    out.setStatus(parseEntitlementStatus(entity.getStatus()));
    out.setSource(entity.getSource());
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private List<String> normalizeList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String value : values) {
      String normalized = normalizeValue(value);
      if (!normalized.isEmpty()) {
        out.add(normalized);
      }
    }
    return out;
  }

  private String normalizeValue(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.US);
  }

  private List<String> parseList(String json) {
    if (json == null || json.isBlank()) {
      return new ArrayList<>();
    }
    try {
      List<String> values = JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
      List<String> out = new ArrayList<>();
      for (String value : values) {
        String normalized = normalizeValue(value);
        if (!normalized.isEmpty()) {
          out.add(normalized);
        }
      }
      return out;
    } catch (Exception e) {
      return new ArrayList<>();
    }
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

  private MarketDataLicenseStatus parseLicenseStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return MarketDataLicenseStatus.ACTIVE;
    }
    try {
      return MarketDataLicenseStatus.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      return MarketDataLicenseStatus.ACTIVE;
    }
  }

  private MarketDataEntitlementStatus parseEntitlementStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return MarketDataEntitlementStatus.ACTIVE;
    }
    try {
      return MarketDataEntitlementStatus.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      return MarketDataEntitlementStatus.ACTIVE;
    }
  }

  private MarketDataEntitlementType parseEntitlementType(String raw) {
    if (raw == null || raw.isBlank()) {
      return MarketDataEntitlementType.SYMBOL;
    }
    try {
      return MarketDataEntitlementType.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      return MarketDataEntitlementType.SYMBOL;
    }
  }

  private static class EntitlementSets {
    boolean global;
    Set<String> symbols = new HashSet<>();
    Set<String> exchanges = new HashSet<>();
    Set<String> assetClasses = new HashSet<>();
    Set<String> regions = new HashSet<>();
  }
}

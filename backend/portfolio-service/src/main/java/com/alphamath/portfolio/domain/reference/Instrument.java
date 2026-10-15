package com.alphamath.portfolio.domain.reference;

import com.alphamath.portfolio.domain.execution.AssetClass;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class Instrument {
  private String id;
  private String symbol;
  private String name;
  private AssetClass assetClass;
  private InstrumentType instrumentType;
  private String exchangeCode;
  private String currency;
  private InstrumentStatus status = InstrumentStatus.ACTIVE;
  private Map<String, String> externalIds = new LinkedHashMap<>();
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
  private Instant updatedAt;
}

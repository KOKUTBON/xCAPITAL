package com.alphamath.portfolio.application.broker;

import com.alphamath.portfolio.domain.broker.BrokerDefinition;
import com.alphamath.portfolio.domain.broker.BrokerRecommendation;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BrokerRoutingService {
  private final BrokerCatalog catalog;
  private final BrokerAccountRepository accounts;
  private final TenantContext tenantContext;

  public BrokerRoutingService(BrokerCatalog catalog, BrokerAccountRepository accounts, TenantContext tenantContext) {
    this.catalog = catalog;
    this.accounts = accounts;
    this.tenantContext = tenantContext;
  }

  public List<BrokerRecommendation> recommend(String userId,
                                              AssetClass assetClass,
                                              Region region,
                                              OrderType orderType,
                                              TimeInForce timeInForce,
                                              String currency) {
    String orgId = tenantContext.getOrgId();
    List<BrokerAccountEntity> linked = orgId == null
        ? accounts.findByUserIdOrderByCreatedAtDesc(userId)
        : accounts.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    linked = linked.stream().filter(row -> tenantContext.owns(userId, row.getUserId(), row.getOrgId())).toList();
    List<BrokerRecommendation> out = new ArrayList<>();

    if (linked.isEmpty()) {
      for (BrokerDefinition def : catalog.list()) {
        BrokerRecommendation rec = fromDefinition(def, null, assetClass, region, orderType, timeInForce, currency);
        if (rec != null) out.add(rec);
      }
    } else {
      for (BrokerAccountEntity entity : linked) {
        BrokerDefinition def = catalog.find(entity.getProviderId());
        if (def == null) continue;
        BrokerRecommendation rec = fromAccount(def, entity, assetClass, region, orderType, timeInForce, currency);
        if (rec != null) out.add(rec);
      }
    }

    out.sort(Comparator.comparingInt(BrokerRecommendation::getScore).reversed());
    return out;
  }

  private BrokerRecommendation fromAccount(BrokerDefinition def,
                                           BrokerAccountEntity entity,
                                           AssetClass assetClass,
                                           Region region,
                                           OrderType orderType,
                                           TimeInForce timeInForce,
                                           String currency) {
    List<AssetClass> supportedAssets = parseAssetClasses(entity.getAssetClassesJson());
    if (assetClass != null && !supportedAssets.contains(assetClass)) {
      return null;
    }
    if (region != null && entity.getRegion() != null && region != entity.getRegion()) {
      return null;
    }
    if (orderType != null && !def.getOrderTypes().contains(orderType)) {
      return null;
    }
    if (timeInForce != null && !def.getTimeInForce().contains(timeInForce)) {
      return null;
    }

    BrokerRecommendation rec = baseRecommendation(def);
    rec.setAccountId(entity.getId());
    rec.setRegion(entity.getRegion());
    rec.setAssetClasses(supportedAssets);

    int score = def.getScore();
    if (assetClass != null) {
      score += 5;
      rec.getReasons().add("Supports " + assetClass.name());
    }
    if (region != null) {
      score += 3;
      rec.getReasons().add("Region match: " + region.name());
    }
    if (orderType != null) {
      score += 2;
      rec.getReasons().add("Order type: " + orderType.name());
    }
    if (timeInForce != null) {
      score += 1;
      rec.getReasons().add("TIF: " + timeInForce.name());
    }
    if (currency != null && entity.getBaseCurrency() != null && currency.equalsIgnoreCase(entity.getBaseCurrency())) {
      score += 1;
      rec.getReasons().add("Base currency: " + entity.getBaseCurrency());
    }
    rec.setScore(score);
    return rec;
  }

  private BrokerRecommendation fromDefinition(BrokerDefinition def,
                                              String accountId,
                                              AssetClass assetClass,
                                              Region region,
                                              OrderType orderType,
                                              TimeInForce timeInForce,
                                              String currency) {
    if (assetClass != null && !def.getAssetClasses().contains(assetClass)) {
      return null;
    }
    if (region != null && !def.getRegions().contains(region) && !def.getRegions().contains(Region.GLOBAL)) {
      return null;
    }
    if (orderType != null && !def.getOrderTypes().contains(orderType)) {
      return null;
    }
    if (timeInForce != null && !def.getTimeInForce().contains(timeInForce)) {
      return null;
    }

    BrokerRecommendation rec = baseRecommendation(def);
    rec.setAccountId(accountId);
    if (region != null) {
      rec.setRegion(region);
      rec.getReasons().add("Region match: " + region.name());
    }
    if (assetClass != null) {
      rec.getReasons().add("Supports " + assetClass.name());
    }
    if (orderType != null) {
      rec.getReasons().add("Order type: " + orderType.name());
    }
    if (timeInForce != null) {
      rec.getReasons().add("TIF: " + timeInForce.name());
    }
    if (currency != null) {
      rec.getReasons().add("Currency: " + currency.toUpperCase());
    }
    rec.setScore(def.getScore());
    return rec;
  }

  private BrokerRecommendation baseRecommendation(BrokerDefinition def) {
    BrokerRecommendation rec = new BrokerRecommendation();
    rec.setBrokerId(def.getId());
    rec.setBrokerName(def.getName());
    rec.setIntegrationStatus(def.getIntegrationStatus());
    rec.setAssetClasses(new ArrayList<>(def.getAssetClasses()));
    rec.setOrderTypes(new ArrayList<>(def.getOrderTypes()));
    rec.setTimeInForce(new ArrayList<>(def.getTimeInForce()));
    return rec;
  }

  private List<AssetClass> parseAssetClasses(String json) {
    if (json == null || json.isBlank()) {
      return new ArrayList<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<AssetClass>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }
}

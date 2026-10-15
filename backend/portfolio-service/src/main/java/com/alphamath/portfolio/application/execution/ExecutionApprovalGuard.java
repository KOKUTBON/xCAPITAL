package com.alphamath.portfolio.application.execution;

import com.alphamath.portfolio.domain.trade.ExecutionMode;
import com.alphamath.portfolio.domain.trade.TradeStatus;
import com.alphamath.portfolio.domain.execution.ExecutionOrder;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.trade.TradeSide;
import com.alphamath.portfolio.infrastructure.persistence.*;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Persisted, tenant-scoped authorization; callers cannot authorize a DTO themselves. */
@Component
public class ExecutionApprovalGuard {
  private final TradeProposalRepository proposals;
  private final TradeOrderRepository orders;
  private final TenantContext tenant;

  public ExecutionApprovalGuard(TradeProposalRepository proposals, TradeOrderRepository orders, TenantContext tenant) {
    this.proposals = proposals;
    this.orders = orders;
    this.tenant = tenant;
  }

  public TradeProposalEntity requireApproved(String userId, String proposalId) {
    TradeProposalEntity p = proposals.findById(proposalId).orElse(null);
    if (p == null || !tenant.owns(userId, p.getUserId(), p.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found");
    }
    if (p.getExecutionMode() != ExecutionMode.LIVE
        || !(TradeStatus.APPROVED.name().equals(p.getStatus()) || TradeStatus.SUBMITTED.name().equals(p.getStatus()))
        || !userId.equals(p.getApprovedBy()) || p.getApprovedAt() == null || p.getApprovalHash() == null
        || !p.getApprovalHash().equals(hash(p, orders.findByProposalIdOrderByCreatedAtAsc(p.getId())))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Persisted approval of unchanged live orders is required");
    }
    return p;
  }

  public TradeProposalEntity requireBoundIntent(String userId, ExecutionIntentEntity intent) {
    if (intent == null || !tenant.owns(userId, intent.getUserId(), intent.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution intent not found");
    }
    TradeProposalEntity p = requireApproved(userId, intent.getProposalId());
    if (!Objects.equals(intent.getId(), p.getExecutionIntentId())
        || !Objects.equals(intent.getApprovalHash(), p.getApprovalHash())
        || intent.getBrokerAccountId() == null || intent.getRegion() != p.getRegion()
        || intent.getAssetClass() != p.getAssetClass()
        || (p.getProviderPreference() != null && !p.getProviderPreference().isBlank()
            && !Objects.equals(p.getProviderPreference(), intent.getProviderId()))
        || !Objects.equals(intent.getOrdersJson(), JsonUtils.toJson(executionOrders(p)))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Execution intent is not bound to the approved proposal");
    }
    return p;
  }

  public List<ExecutionOrder> executionOrders(TradeProposalEntity p) {
    return orders.findByProposalIdOrderByCreatedAtAsc(p.getId()).stream()
        .sorted(Comparator.comparing(TradeOrderEntity::getId)).map(o -> {
          ExecutionOrder e = new ExecutionOrder();
          e.setSymbol(o.getSymbol());
          e.setSide(TradeSide.valueOf(o.getSide()));
          e.setQuantity(o.getQuantity());
          e.setPrice(o.getPrice());
          e.setOrderType(p.getOrderType());
          e.setTimeInForce(p.getTimeInForce());
          e.setLimitPrice(p.getOrderType() == OrderType.LIMIT ? o.getPrice() : null);
          return e;
        }).toList();
  }

  public static String hash(TradeProposalEntity p, List<TradeOrderEntity> rows) {
    if (rows == null || rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal has no executable orders");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("version", 1);
    payload.put("id", p.getId());
    payload.put("user", p.getUserId());
    payload.put("org", p.getOrgId());
    payload.put("account", p.getAccountId());
    payload.put("mode", p.getExecutionMode());
    payload.put("region", p.getRegion());
    payload.put("assetClass", p.getAssetClass());
    payload.put("provider", p.getProviderPreference());
    payload.put("orderType", p.getOrderType());
    payload.put("timeInForce", p.getTimeInForce());
    List<Map<String, Object>> canonicalOrders = new ArrayList<>();
    rows.stream().sorted(Comparator.comparing(TradeOrderEntity::getId)).forEach(o -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", o.getId());
      item.put("symbol", o.getSymbol());
      item.put("side", o.getSide());
      item.put("quantity", o.getQuantity());
      item.put("price", o.getPrice());
      item.put("notional", o.getNotional());
      item.put("fee", o.getFee());
      canonicalOrders.add(item);
    });
    payload.put("orders", canonicalOrders);
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(JsonUtils.toJson(payload).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot hash approval", e);
    }
  }
}

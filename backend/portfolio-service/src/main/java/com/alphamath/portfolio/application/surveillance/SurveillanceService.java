package com.alphamath.portfolio.application.surveillance;

import com.alphamath.portfolio.domain.surveillance.SurveillanceAlert;
import com.alphamath.portfolio.domain.surveillance.SurveillanceSeverity;
import com.alphamath.portfolio.domain.trade.TradeOrder;
import com.alphamath.portfolio.infrastructure.persistence.SurveillanceAlertEntity;
import com.alphamath.portfolio.infrastructure.persistence.SurveillanceAlertRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SurveillanceService {
  private final SurveillanceAlertRepository alerts;
  private final TenantContext tenantContext;
  private final double largeOrderNotional;
  private final int maxOrdersPerProposal;

  public SurveillanceService(SurveillanceAlertRepository alerts,
                             TenantContext tenantContext,
                             @Value("${alphamath.surveillance.largeOrderNotional:100000}") double largeOrderNotional,
                             @Value("${alphamath.surveillance.maxOrdersPerProposal:20}") int maxOrdersPerProposal) {
    this.alerts = alerts;
    this.tenantContext = tenantContext;
    this.largeOrderNotional = Math.max(0.0, largeOrderNotional);
    this.maxOrdersPerProposal = Math.max(1, maxOrdersPerProposal);
  }

  public List<SurveillanceAlert> evaluate(String userId, String proposalId, List<TradeOrder> fills) {
    List<SurveillanceAlert> out = new ArrayList<>();
    if (fills == null || fills.isEmpty()) {
      return out;
    }

    if (fills.size() > maxOrdersPerProposal) {
      out.add(createAlert(userId, "HIGH_ORDER_COUNT", SurveillanceSeverity.MEDIUM,
          null, null, "Order count " + fills.size() + " exceeds " + maxOrdersPerProposal));
    }

    for (TradeOrder order : fills) {
      if (order.getNotional() >= largeOrderNotional) {
        out.add(createAlert(userId, "LARGE_ORDER", SurveillanceSeverity.HIGH,
            order.getSymbol(), order.getNotional(),
            "Notional " + order.getNotional() + " exceeds " + largeOrderNotional));
      }
    }

    return out;
  }

  public List<SurveillanceAlert> list(String userId, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    List<SurveillanceAlert> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<SurveillanceAlertEntity> rows = orgId == null
        ? alerts.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
        : alerts.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, PageRequest.of(0, size));
    for (SurveillanceAlertEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  private SurveillanceAlert createAlert(String userId, String type, SurveillanceSeverity severity,
                                        String symbol, Double notional, String detail) {
    SurveillanceAlertEntity entity = new SurveillanceAlertEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setAlertType(type);
    entity.setSeverity(severity.name());
    entity.setSymbol(symbol);
    entity.setNotional(notional);
    entity.setDetail(detail);
    entity.setCreatedAt(Instant.now());
    alerts.save(entity);
    return toDto(entity);
  }

  private SurveillanceAlert toDto(SurveillanceAlertEntity entity) {
    SurveillanceAlert out = new SurveillanceAlert();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setAlertType(entity.getAlertType());
    out.setSeverity(SurveillanceSeverity.valueOf(entity.getSeverity()));
    out.setSymbol(entity.getSymbol());
    out.setNotional(entity.getNotional());
    out.setDetail(entity.getDetail());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }
}

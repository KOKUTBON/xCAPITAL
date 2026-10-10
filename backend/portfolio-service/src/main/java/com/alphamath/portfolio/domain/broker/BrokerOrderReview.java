package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.trade.AiRecommendation;
import com.alphamath.portfolio.domain.trade.PolicyCheck;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BrokerOrderReview {
  private BrokerOrderPreview preview;
  private List<PolicyCheck> policyChecks = new ArrayList<>();
  private AiRecommendation ai;
  private Map<String, Object> cashImpact = new LinkedHashMap<>();
  private Map<String, Object> positionImpact = new LinkedHashMap<>();
  private List<String> warnings = new ArrayList<>();
  private String disclaimer;
  private Instant createdAt;
}

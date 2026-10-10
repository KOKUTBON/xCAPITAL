package com.alphamath.portfolio.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class AiForecastService {
  private static final Logger log = LoggerFactory.getLogger(AiForecastService.class);
  private final RestClient client;

  public AiForecastService(@Value("${alphamath.ai.baseUrl}") String baseUrl) {
    this.client = RestClient.builder().baseUrl(baseUrl).build();
  }

  public AiForecast predict(List<Double> returns, int horizon) {
    if (returns == null || returns.size() < 30) return null;
    try {
      return client.post()
          .uri("/v1/predict")
          .body(new AiPredictRequest(returns, horizon))
          .retrieve()
          .body(AiForecast.class);
    } catch (Exception e) {
      log.warn("AI forecast unavailable: {}", e.getMessage());
      return null;
    }
  }

  public AiRiskForecast risk(List<Double> returns, int horizon) {
    if (returns == null || returns.size() < 30) return null;
    try {
      return client.post()
          .uri("/v1/risk")
          .body(new AiPredictRequest(returns, horizon))
          .retrieve()
          .body(AiRiskForecast.class);
    } catch (Exception e) {
      log.warn("AI risk forecast unavailable: {}", e.getMessage());
      return null;
    }
  }

  public record AiPredictRequest(List<Double> returns, int horizon) {}

  public record AiForecast(
      @JsonProperty("expected_return") double expectedReturn,
      @JsonProperty("volatility") double volatility,
      @JsonProperty("p_up") double pUp,
      @JsonProperty("confidence") double confidence,
      @JsonProperty("disclaimer") String disclaimer
  ) {}

  public record AiRiskForecast(
      @JsonProperty("volatility") double volatility,
      @JsonProperty("max_drawdown") double maxDrawdown,
      @JsonProperty("confidence") double confidence,
      @JsonProperty("regime") String regime,
      @JsonProperty("model_version") String modelVersion,
      @JsonProperty("training_window_start") String trainingWindowStart,
      @JsonProperty("training_window_end") String trainingWindowEnd,
      @JsonProperty("disclaimer") String disclaimer
  ) {}
}

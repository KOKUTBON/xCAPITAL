package com.alphamath.gateway.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfig {

  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> Mono.just(resolveKey(exchange));
  }

  private String resolveKey(ServerWebExchange exchange) {
    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
    if (userId != null && !userId.isBlank()) {
      return "user:" + userId.trim();
    }
    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
    if (remote != null && remote.getAddress() != null) {
      return "ip:" + remote.getAddress().getHostAddress();
    }
    return "anonymous";
  }
}

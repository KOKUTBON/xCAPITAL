package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.alert.AlertService;
import com.alphamath.portfolio.domain.alert.Alert;
import com.alphamath.portfolio.domain.alert.AlertRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
  private final AlertService alerts;

  public AlertController(AlertService alerts) {
    this.alerts = alerts;
  }

  @PostMapping
  public Alert create(@RequestBody AlertRequest req, Principal principal) {
    return alerts.create(userId(principal), req);
  }

  @GetMapping
  public List<Alert> list(@RequestParam(required = false) String status,
                          @RequestParam(required = false) Integer limit,
                          Principal principal) {
    return alerts.list(userId(principal), status, limit == null ? 0 : limit);
  }

  @PostMapping("/{id}/status")
  public Alert updateStatus(@PathVariable String id, @RequestBody StatusRequest req, Principal principal) {
    return alerts.updateStatus(userId(principal), id, req == null ? null : req.status);
  }

  @PostMapping("/{id}/trigger")
  public Alert trigger(@PathVariable String id, @RequestBody TriggerRequest req, Principal principal) {
    return alerts.trigger(userId(principal), id, req == null ? Map.of() : req.metadata);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  public static class StatusRequest {
    public String status;
  }

  public static class TriggerRequest {
    public Map<String, Object> metadata = Map.of();
  }
}

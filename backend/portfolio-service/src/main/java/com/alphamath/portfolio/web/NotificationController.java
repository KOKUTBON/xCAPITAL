package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.notification.NotificationDeliveryService;
import com.alphamath.portfolio.application.notification.NotificationDestinationService;
import com.alphamath.portfolio.application.notification.NotificationPreferenceService;
import com.alphamath.portfolio.application.notification.NotificationService;
import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationDelivery;
import com.alphamath.portfolio.domain.notification.NotificationDestination;
import com.alphamath.portfolio.domain.notification.NotificationDestinationRequest;
import com.alphamath.portfolio.domain.notification.NotificationPreference;
import com.alphamath.portfolio.domain.notification.NotificationPreferenceRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService notifications;
  private final NotificationPreferenceService preferences;
  private final NotificationDestinationService destinations;
  private final NotificationDeliveryService deliveries;

  public NotificationController(NotificationService notifications,
                                NotificationPreferenceService preferences,
                                NotificationDestinationService destinations,
                                NotificationDeliveryService deliveries) {
    this.notifications = notifications;
    this.preferences = preferences;
    this.destinations = destinations;
    this.deliveries = deliveries;
  }

  @GetMapping
  public List<Notification> list(@RequestParam(required = false) String status,
                                 @RequestParam(required = false, defaultValue = "50") int limit,
                                 Principal principal) {
    return notifications.list(userId(principal), status, limit);
  }

  @PostMapping("/{id}/read")
  public Notification markRead(@PathVariable String id, Principal principal) {
    return notifications.markRead(userId(principal), id);
  }

  @GetMapping("/preferences")
  public List<NotificationPreference> listPreferences(Principal principal) {
    return preferences.list(userId(principal));
  }

  @PostMapping("/preferences")
  public NotificationPreference upsertPreference(@RequestBody NotificationPreferenceRequest req, Principal principal) {
    return preferences.upsert(userId(principal), req);
  }

  @GetMapping("/destinations")
  public List<NotificationDestination> listDestinations(@RequestParam(required = false) String channel,
                                                        Principal principal) {
    return destinations.list(userId(principal), channel);
  }

  @PostMapping("/destinations")
  public NotificationDestination createDestination(@RequestBody NotificationDestinationRequest req,
                                                   Principal principal) {
    return destinations.create(userId(principal), req);
  }

  @PostMapping("/destinations/{id}/verify")
  public NotificationDestination verifyDestination(@PathVariable String id, Principal principal) {
    return destinations.verify(userId(principal), id);
  }

  @PostMapping("/destinations/{id}/disable")
  public NotificationDestination disableDestination(@PathVariable String id, Principal principal) {
    return destinations.disable(userId(principal), id);
  }

  @GetMapping("/deliveries")
  public List<NotificationDelivery> listDeliveries(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false, defaultValue = "50") int limit,
                                                   Principal principal) {
    return deliveries.list(userId(principal), status, limit);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}

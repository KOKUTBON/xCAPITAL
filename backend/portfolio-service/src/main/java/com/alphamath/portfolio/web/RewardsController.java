package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.rewards.RewardsService;
import com.alphamath.portfolio.domain.rewards.RewardEnrollment;
import com.alphamath.portfolio.domain.rewards.RewardOffer;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rewards")
public class RewardsController {
  private final RewardsService rewards;

  public RewardsController(RewardsService rewards) {
    this.rewards = rewards;
  }

  @GetMapping("/offers")
  public List<RewardOffer> offers() {
    return rewards.listOffers();
  }

  @GetMapping("/enrollments")
  public List<RewardEnrollment> enrollments(Principal principal) {
    return rewards.listEnrollments(userId(principal));
  }

  @PostMapping("/enroll")
  public RewardEnrollment enroll(@RequestBody EnrollRequest req, Principal principal) {
    if (req == null || req.offerId == null || req.offerId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offerId is required");
    }
    return rewards.enroll(userId(principal), req.offerId.trim());
  }

  @PostMapping("/evaluate")
  public List<RewardEnrollment> evaluate(@RequestBody(required = false) EvaluateRequest req, Principal principal) {
    String userId = userId(principal);
    if (req != null && req.enrollmentId != null && !req.enrollmentId.isBlank()) {
      return List.of(rewards.evaluate(userId, req.enrollmentId.trim()));
    }
    List<RewardEnrollment> out = new ArrayList<>();
    for (RewardEnrollment enrollment : rewards.listEnrollments(userId)) {
      if (enrollment.getStatus().name().equals("PENDING")) {
        out.add(rewards.evaluate(userId, enrollment.getId()));
      } else {
        out.add(enrollment);
      }
    }
    return out;
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  @Data
  public static class EnrollRequest {
    public String offerId;
  }

  @Data
  public static class EvaluateRequest {
    public String enrollmentId;
  }
}

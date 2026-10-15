package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.ai.ModelRegistryService;
import com.alphamath.portfolio.domain.ai.ModelRegisterRequest;
import com.alphamath.portfolio.domain.ai.ModelRegistryEntry;
import com.alphamath.portfolio.domain.ai.ModelStatus;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/models")
public class AiModelRegistryController {
  private final ModelRegistryService registry;

  public AiModelRegistryController(ModelRegistryService registry) {
    this.registry = registry;
  }

  @PostMapping
  public ModelRegistryEntry register(@Valid @RequestBody ModelRegisterRequest req) {
    return registry.register(req);
  }

  @GetMapping
  public List<ModelRegistryEntry> list(@RequestParam(required = false) String modelName,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false, defaultValue = "50") int limit) {
    return registry.list(modelName, status, limit);
  }

  @GetMapping("/{id}")
  public ModelRegistryEntry get(@PathVariable String id) {
    return registry.get(id);
  }

  @PostMapping("/{id}/status")
  public ModelRegistryEntry updateStatus(@PathVariable String id, @Valid @RequestBody StatusRequest req) {
    ModelStatus status;
    try {
      status = ModelStatus.valueOf(req.status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
    }
    return registry.updateStatus(id, status);
  }

  @Data
  public static class StatusRequest {
    @jakarta.validation.constraints.NotNull
    public String status;
  }
}

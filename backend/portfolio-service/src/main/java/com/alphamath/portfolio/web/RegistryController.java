package com.alphamath.portfolio.web;

import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/registry")
public class RegistryController {

  @GetMapping("/formulas")
  public String formulas() {
    // Loads the living registry from repo path inside container image.
    // In production: move to classpath resource or a config-backed store (S3/GCS/DB).
    String path = "/app/math-library/registry/formulas.json";
    try (InputStream in = RegistryController.class.getResourceAsStream("/math-library/registry/formulas.json")) {
      if (in != null) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (Exception ignored) {}

    // fallback: if not on classpath, return hint
    return "{\"error\":\"Registry not on classpath\",\"hint\":\"Mount math-library/registry/formulas.json into service image or include as classpath resource.\"}";
  }
}

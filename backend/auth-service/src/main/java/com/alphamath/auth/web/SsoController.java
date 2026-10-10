package com.alphamath.auth.web;

import com.alphamath.auth.sso.SsoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/sso")
public class SsoController {
  private final SsoService sso;

  public SsoController(SsoService sso) {
    this.sso = sso;
  }

  @GetMapping("/oidc/start")
  public ResponseEntity<SsoService.SsoStartResponse> oidcStart(@RequestParam(name = "orgId", required = false) Long orgId,
                                                               @RequestParam(name = "orgSlug", required = false) String orgSlug,
                                                               @RequestParam(name = "providerId", required = false) Long providerId,
                                                               @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                                               @RequestParam(name = "redirect", required = false, defaultValue = "false") boolean redirect) {
    SsoService.SsoStartResponse start = sso.startOidc(orgId, orgSlug, providerId, redirectUri, redirect);
    if (redirect) {
      return ResponseEntity.status(302).header(HttpHeaders.LOCATION, start.authorizationUrl()).build();
    }
    return ResponseEntity.ok(start);
  }

  @GetMapping("/oidc/callback")
  public SsoService.SsoLoginResponse oidcCallback(@RequestParam(name = "code") String code,
                                                  @RequestParam(name = "state") String state) {
    return sso.handleOidcCallback(code, state);
  }

  @GetMapping("/saml/start")
  public ResponseEntity<SsoService.SsoStartResponse> samlStart(@RequestParam(name = "orgId", required = false) Long orgId,
                                                               @RequestParam(name = "orgSlug", required = false) String orgSlug,
                                                               @RequestParam(name = "providerId", required = false) Long providerId,
                                                               @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                                               @RequestParam(name = "redirect", required = false, defaultValue = "false") boolean redirect) {
    SsoService.SsoStartResponse start = sso.startSaml(orgId, orgSlug, providerId, redirectUri, redirect);
    if (redirect) {
      return ResponseEntity.status(302).header(HttpHeaders.LOCATION, start.authorizationUrl()).build();
    }
    return ResponseEntity.ok(start);
  }

  @PostMapping("/saml/acs")
  public SsoService.SsoLoginResponse samlAcs(@RequestParam("SAMLResponse") String samlResponse,
                                             @RequestParam("RelayState") String relayState) {
    return sso.handleSamlResponse(samlResponse, relayState);
  }
}

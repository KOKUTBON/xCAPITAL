package com.alphamath.auth.web;

import com.alphamath.auth.sso.SsoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SsoContractTest {

  private MockMvc mvc;
  private StubSsoService sso;

  @BeforeEach
  void setUp() {
    sso = new StubSsoService();
    mvc = MockMvcBuilders.standaloneSetup(new SsoController(sso)).build();
  }

  @Test
  void oidcStartReturnsContractPayload() throws Exception {
    sso.oidcStartResponse =
        new SsoService.SsoStartResponse(
            "https://idp.example.com/authorize?state=state-oidc",
            "state-oidc",
            42L,
            "acme",
            "OIDC",
            false);

    mvc.perform(get("/api/v1/auth/sso/oidc/start")
            .param("orgId", "42")
            .param("orgSlug", "acme")
            .param("providerId", "5"))
        .andExpect(status().isOk())
        .andExpect(content().json(fixture("fixtures/sso/oidc-start-response.json")));
  }

  @Test
  void oidcStartRedirectModeReturns302() throws Exception {
    sso.oidcStartResponse =
        new SsoService.SsoStartResponse(
            "https://idp.example.com/authorize?state=state-oidc",
            "state-oidc",
            42L,
            "acme",
            "OIDC",
            true);

    mvc.perform(get("/api/v1/auth/sso/oidc/start")
            .param("orgId", "42")
            .param("orgSlug", "acme")
            .param("providerId", "5")
            .param("redirectUri", "https://app.example.com/callback")
            .param("redirect", "true"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://idp.example.com/authorize?state=state-oidc"));
  }

  @Test
  void oidcCallbackReturnsContractPayload() throws Exception {
    sso.oidcCallbackResponse =
        new SsoService.SsoLoginResponse(
            "jwt-token-value",
            List.of("USER"),
            42L,
            "acme",
            List.of("ADMIN"),
            9001L,
            "analyst@acme.test");

    mvc.perform(get("/api/v1/auth/sso/oidc/callback")
            .param("code", "code-1")
            .param("state", "state-oidc"))
        .andExpect(status().isOk())
        .andExpect(content().json(fixture("fixtures/sso/oidc-callback-response.json")));
  }

  @Test
  void oidcCallbackRejectsInvalidState() throws Exception {
    sso.oidcCallbackException = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session state");

    mvc.perform(get("/api/v1/auth/sso/oidc/callback")
            .param("code", "bad-code")
            .param("state", "bad-state"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void samlStartReturnsContractPayload() throws Exception {
    sso.samlStartResponse =
        new SsoService.SsoStartResponse(
            "https://idp.example.com/saml?RelayState=state-saml",
            "state-saml",
            42L,
            "acme",
            "SAML",
            false);

    mvc.perform(get("/api/v1/auth/sso/saml/start")
            .param("orgId", "42")
            .param("orgSlug", "acme")
            .param("providerId", "9"))
        .andExpect(status().isOk())
        .andExpect(content().json(fixture("fixtures/sso/saml-start-response.json")));
  }

  @Test
  void samlAcsRejectsInvalidSignature() throws Exception {
    sso.samlAcsException = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SAML signature");

    mvc.perform(post("/api/v1/auth/sso/saml/acs")
            .param("SAMLResponse", "invalid-response")
            .param("RelayState", "state-saml"))
        .andExpect(status().isBadRequest());
  }

  private String fixture(String path) throws IOException {
    ClassPathResource resource = new ClassPathResource(path);
    return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
  }

  private static class StubSsoService extends SsoService {
    private SsoStartResponse oidcStartResponse;
    private RuntimeException oidcStartException;
    private SsoLoginResponse oidcCallbackResponse;
    private RuntimeException oidcCallbackException;
    private SsoStartResponse samlStartResponse;
    private RuntimeException samlStartException;
    private SsoLoginResponse samlAcsResponse;
    private RuntimeException samlAcsException;

    StubSsoService() {
      super(null, null, null, null, null, null, null, null, "https://app.example.com", "urn:test:sp", 10);
    }

    @Override
    public SsoStartResponse startOidc(Long orgId, String orgSlug, Long providerId, String redirectOverride, boolean redirect) {
      if (oidcStartException != null) {
        throw oidcStartException;
      }
      return oidcStartResponse;
    }

    @Override
    public SsoLoginResponse handleOidcCallback(String code, String state) {
      if (oidcCallbackException != null) {
        throw oidcCallbackException;
      }
      return oidcCallbackResponse;
    }

    @Override
    public SsoStartResponse startSaml(Long orgId, String orgSlug, Long providerId, String redirectOverride, boolean redirect) {
      if (samlStartException != null) {
        throw samlStartException;
      }
      return samlStartResponse;
    }

    @Override
    public SsoLoginResponse handleSamlResponse(String samlResponse, String relayState) {
      if (samlAcsException != null) {
        throw samlAcsException;
      }
      return samlAcsResponse;
    }
  }
}

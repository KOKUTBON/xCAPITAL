package com.alphamath.auth.sso;

import com.alphamath.auth.org.IdentityProviderEntity;
import com.alphamath.auth.org.IdentityProviderRepository;
import com.alphamath.auth.org.OrganizationEntity;
import com.alphamath.auth.org.OrganizationMemberRepository;
import com.alphamath.auth.org.OrganizationRepository;
import com.alphamath.auth.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoServiceSecurityEdgeCaseTest {

  @Mock
  private OrganizationRepository orgs;
  @Mock
  private IdentityProviderRepository providers;
  @Mock
  private OrganizationMemberRepository members;
  @Mock
  private UserRepository users;
  @Mock
  private FederatedIdentityRepository federated;
  @Mock
  private SsoLoginSessionRepository sessions;

  private TestableSsoService service;

  @BeforeEach
  void setUp() {
    service = new TestableSsoService(orgs, providers, members, users, federated, sessions);
  }

  @Test
  void parseOidcIdTokenSelectsUsableRsaJwkAndValidatesClaims() throws Exception {
    KeyPair keys = generateRsaKeyPair();
    IdentityProviderEntity provider = oidcProvider();
    provider.setJwksUrl("https://jwks.example.com/keys");
    service.putJson(provider.getJwksUrl(), jwksWithUnsupportedAndValidKey(keys));

    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setNonce("nonce-1");

    String token = buildOidcToken(keys,
        "https://issuer.example.com",
        "client-1",
        "nonce-1",
        Instant.now().plusSeconds(600),
        Instant.now().minusSeconds(30),
        Instant.now().minusSeconds(30));

    Claims claims = service.parseOidcIdToken(token, provider, session, null);

    assertEquals("user-subject", claims.getSubject());
  }

  @Test
  void parseOidcIdTokenFallsBackToJwksWhenCertificateIsInvalid() throws Exception {
    KeyPair keys = generateRsaKeyPair();
    IdentityProviderEntity provider = oidcProvider();
    provider.setX509Cert("not-a-valid-certificate");
    provider.setJwksUrl("https://jwks.example.com/fallback");
    service.putJson(provider.getJwksUrl(), jwksWithValidRsaKey(keys));

    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setNonce("nonce-1");

    String token = buildOidcToken(keys,
        "https://issuer.example.com",
        "client-1",
        "nonce-1",
        Instant.now().plusSeconds(600),
        Instant.now().minusSeconds(30),
        Instant.now().minusSeconds(30));

    Claims claims = service.parseOidcIdToken(token, provider, session, null);

    assertEquals("user-subject", claims.getSubject());
  }

  @Test
  void parseOidcIdTokenRejectsExpiredToken() throws Exception {
    KeyPair keys = generateRsaKeyPair();
    IdentityProviderEntity provider = oidcProvider();
    provider.setJwksUrl("https://jwks.example.com/expired");
    service.putJson(provider.getJwksUrl(), jwksWithValidRsaKey(keys));

    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setNonce("nonce-1");

    String token = buildOidcToken(keys,
        "https://issuer.example.com",
        "client-1",
        "nonce-1",
        Instant.now().minusSeconds(600),
        Instant.now().minusSeconds(1200),
        Instant.now().minusSeconds(1200));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.parseOidcIdToken(token, provider, session, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().toLowerCase().contains("expired"));
  }

  @Test
  void parseOidcIdTokenRejectsFutureIssuedAt() throws Exception {
    KeyPair keys = generateRsaKeyPair();
    IdentityProviderEntity provider = oidcProvider();
    provider.setJwksUrl("https://jwks.example.com/iat");
    service.putJson(provider.getJwksUrl(), jwksWithValidRsaKey(keys));

    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setNonce("nonce-1");

    String token = buildOidcToken(keys,
        "https://issuer.example.com",
        "client-1",
        "nonce-1",
        Instant.now().plusSeconds(600),
        Instant.now().minusSeconds(30),
        Instant.now().plusSeconds(600));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.parseOidcIdToken(token, provider, session, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().toLowerCase().contains("issued-at"));
  }

  @Test
  void handleSamlResponseRejectsMissingInResponseToWhenSessionHasRequestId() {
    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setState("relay-1");
    session.setRequestId("_req-123");
    session.setProviderId(1L);
    session.setOrgId(10L);
    session.setExpiresAt(Instant.now().plusSeconds(300));
    when(sessions.findByState("relay-1")).thenReturn(Optional.of(session));

    IdentityProviderEntity provider = new IdentityProviderEntity();
    provider.setId(1L);
    provider.setProviderType("SAML");
    when(providers.findById(1L)).thenReturn(Optional.of(provider));

    OrganizationEntity org = new OrganizationEntity();
    org.setId(10L);
    org.setSlug("acme");
    when(orgs.findById(10L)).thenReturn(Optional.of(org));

    String saml = base64SamlResponse(null, Instant.now().minusSeconds(30), Instant.now().plusSeconds(300), "urn:test:sp");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.handleSamlResponse(saml, "relay-1"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("InResponseTo missing"));
  }

  @Test
  void handleSamlResponseRejectsInvalidConditionWindow() {
    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setState("relay-2");
    session.setRequestId("_req-456");
    session.setProviderId(2L);
    session.setOrgId(11L);
    session.setExpiresAt(Instant.now().plusSeconds(300));
    when(sessions.findByState("relay-2")).thenReturn(Optional.of(session));

    IdentityProviderEntity provider = new IdentityProviderEntity();
    provider.setId(2L);
    provider.setProviderType("SAML");
    when(providers.findById(2L)).thenReturn(Optional.of(provider));

    OrganizationEntity org = new OrganizationEntity();
    org.setId(11L);
    org.setSlug("acme-2");
    when(orgs.findById(11L)).thenReturn(Optional.of(org));

    String saml = base64SamlResponse("_req-456", Instant.now().plusSeconds(600), Instant.now().plusSeconds(300), "urn:test:sp");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.handleSamlResponse(saml, "relay-2"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().toLowerCase().contains("validity window"));
  }

  private IdentityProviderEntity oidcProvider() {
    IdentityProviderEntity provider = new IdentityProviderEntity();
    provider.setProviderType("OIDC");
    provider.setIssuer("https://issuer.example.com");
    provider.setClientId("client-1");
    return provider;
  }

  private String buildOidcToken(KeyPair keys,
                                String issuer,
                                String audience,
                                String nonce,
                                Instant exp,
                                Instant nbf,
                                Instant iat) {
    return Jwts.builder()
        .issuer(issuer)
        .subject("user-subject")
        .audience().add(audience).and()
        .claim("nonce", nonce)
        .issuedAt(Date.from(iat))
        .notBefore(Date.from(nbf))
        .expiration(Date.from(exp))
        .signWith(keys.getPrivate())
        .compact();
  }

  private KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private Map<String, Object> jwksWithUnsupportedAndValidKey(KeyPair keys) {
    RSAPublicKey rsa = (RSAPublicKey) keys.getPublic();
    Map<String, Object> unsupported = Map.of(
        "kty", "EC",
        "kid", "ec-1",
        "use", "sig"
    );
    Map<String, Object> valid = Map.of(
        "kty", "RSA",
        "kid", "rsa-1",
        "use", "sig",
        "n", base64UrlUnsigned(rsa.getModulus().toByteArray()),
        "e", base64UrlUnsigned(rsa.getPublicExponent().toByteArray())
    );
    return Map.of("keys", List.of(unsupported, valid));
  }

  private Map<String, Object> jwksWithValidRsaKey(KeyPair keys) {
    RSAPublicKey rsa = (RSAPublicKey) keys.getPublic();
    Map<String, Object> valid = Map.of(
        "kty", "RSA",
        "kid", "rsa-1",
        "use", "sig",
        "n", base64UrlUnsigned(rsa.getModulus().toByteArray()),
        "e", base64UrlUnsigned(rsa.getPublicExponent().toByteArray())
    );
    return Map.of("keys", List.of(valid));
  }

  private String base64UrlUnsigned(byte[] value) {
    byte[] normalized = value;
    if (value.length > 1 && value[0] == 0) {
      normalized = java.util.Arrays.copyOfRange(value, 1, value.length);
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(normalized);
  }

  private String base64SamlResponse(String inResponseTo, Instant notBefore, Instant notOnOrAfter, String audience) {
    String inResponseToAttr = inResponseTo == null ? "" : " InResponseTo=\"" + inResponseTo + "\"";
    String xml = "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"" + inResponseToAttr + ">"
        + "<saml:Issuer xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">https://issuer.example.com</saml:Issuer>"
        + "<saml:Assertion xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
        + "<saml:Subject><saml:NameID>user@example.com</saml:NameID></saml:Subject>"
        + "<saml:Conditions NotBefore=\"" + notBefore + "\" NotOnOrAfter=\"" + notOnOrAfter + "\">"
        + "<saml:AudienceRestriction><saml:Audience>" + audience + "</saml:Audience></saml:AudienceRestriction>"
        + "</saml:Conditions>"
        + "<saml:AttributeStatement>"
        + "<saml:Attribute Name=\"email\"><saml:AttributeValue>user@example.com</saml:AttributeValue></saml:Attribute>"
        + "</saml:AttributeStatement>"
        + "</saml:Assertion>"
        + "</samlp:Response>";
    return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
  }

  private static class TestableSsoService extends SsoService {
    private final Map<String, Map<String, Object>> jsonByUrl = new HashMap<>();

    TestableSsoService(OrganizationRepository orgs,
                       IdentityProviderRepository providers,
                       OrganizationMemberRepository members,
                       UserRepository users,
                       FederatedIdentityRepository federated,
                       SsoLoginSessionRepository sessions) {
      super(orgs, providers, members, users, federated, sessions, null, null,
          "https://app.example.com", "urn:test:sp", 10);
    }

    void putJson(String url, Map<String, Object> payload) {
      jsonByUrl.put(url, payload);
    }

    @Override
    Map<String, Object> fetchJson(String url) {
      Map<String, Object> payload = jsonByUrl.get(url);
      if (payload == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to fetch metadata");
      }
      return payload;
    }
  }
}

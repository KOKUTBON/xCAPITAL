package com.alphamath.auth.sso;

import com.alphamath.auth.org.IdentityProviderEntity;
import com.alphamath.auth.org.IdentityProviderRepository;
import com.alphamath.auth.org.OrganizationEntity;
import com.alphamath.auth.org.OrganizationMemberEntity;
import com.alphamath.auth.org.OrganizationMemberRepository;
import com.alphamath.auth.org.OrganizationRepository;
import com.alphamath.auth.org.OrganizationService;
import com.alphamath.auth.security.JwtService;
import com.alphamath.auth.user.UserEntity;
import com.alphamath.auth.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.XMLConstants;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPublicKeySpec;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
public class SsoService {
  private static final long OIDC_CLOCK_SKEW_SECONDS = 60;
  private static final long SAML_CLOCK_SKEW_SECONDS = 30;

  private final OrganizationRepository orgs;
  private final IdentityProviderRepository providers;
  private final OrganizationMemberRepository members;
  private final UserRepository users;
  private final FederatedIdentityRepository federated;
  private final SsoLoginSessionRepository sessions;
  private final OrganizationService organizations;
  private final JwtService jwt;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String baseUrl;
  private final String entityId;
  private final long sessionTtlMinutes;

  public SsoService(OrganizationRepository orgs,
                    IdentityProviderRepository providers,
                    OrganizationMemberRepository members,
                    UserRepository users,
                    FederatedIdentityRepository federated,
                    SsoLoginSessionRepository sessions,
                    OrganizationService organizations,
                    JwtService jwt,
                    @Value("${alphamath.sso.baseUrl}") String baseUrl,
                    @Value("${alphamath.sso.entityId}") String entityId,
                    @Value("${alphamath.sso.sessionTtlMinutes:10}") long sessionTtlMinutes) {
    this.orgs = orgs;
    this.providers = providers;
    this.members = members;
    this.users = users;
    this.federated = federated;
    this.sessions = sessions;
    this.organizations = organizations;
    this.jwt = jwt;
    this.baseUrl = baseUrl;
    this.entityId = entityId;
    this.sessionTtlMinutes = Math.max(1, sessionTtlMinutes);
  }

  public SsoStartResponse startOidc(Long orgId, String orgSlug, Long providerId, String redirectOverride, boolean redirect)
      throws ResponseStatusException {
    OrganizationEntity org = resolveOrg(orgId, orgSlug);
    IdentityProviderEntity provider = resolveProvider(org.getId(), providerId, "OIDC");
    if (provider == null || !provider.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OIDC provider not configured");
    }
    if (provider.getClientId() == null || provider.getClientId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC provider missing clientId");
    }

    OidcEndpoints endpoints = resolveOidcEndpoints(provider);
    String redirectUri = resolveRedirectUri(provider, redirectOverride, baseUrl + "/api/v1/auth/sso/oidc/callback");

    String state = randomToken();
    String nonce = randomToken();
    String codeVerifier = randomToken();
    String codeChallenge = base64Url(sha256(codeVerifier));

    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setId(UUID.randomUUID().toString().replace("-", ""));
    session.setOrgId(org.getId());
    session.setProviderId(provider.getId());
    session.setFlowType("OIDC");
    session.setState(state);
    session.setNonce(nonce);
    session.setCodeVerifier(codeVerifier);
    session.setRedirectUri(redirectUri);
    session.setCreatedAt(Instant.now());
    session.setExpiresAt(Instant.now().plus(sessionTtlMinutes, ChronoUnit.MINUTES));
    sessions.save(session);

    String scope = provider.getScopes();
    if (scope == null || scope.isBlank()) {
      scope = "openid email profile";
    }

    StringBuilder url = new StringBuilder();
    url.append(endpoints.authorizationUrl());
    url.append(endpoints.authorizationUrl().contains("?") ? "&" : "?");
    url.append("response_type=code");
    url.append("&client_id=").append(urlEncode(provider.getClientId()));
    url.append("&redirect_uri=").append(urlEncode(redirectUri));
    url.append("&scope=").append(urlEncode(scope));
    url.append("&state=").append(urlEncode(state));
    url.append("&nonce=").append(urlEncode(nonce));
    url.append("&code_challenge=").append(urlEncode(codeChallenge));
    url.append("&code_challenge_method=S256");

    return new SsoStartResponse(url.toString(), state, org.getId(), org.getSlug(), provider.getProviderType(), redirect);
  }

  public SsoLoginResponse handleOidcCallback(String code, String state) {
    if (code == null || code.isBlank() || state == null || state.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code and state required");
    }
    SsoLoginSessionEntity session = sessions.findByState(state.trim()).orElse(null);
    if (session == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session state");
    }
    if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
      sessions.delete(session);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSO session expired");
    }
    IdentityProviderEntity provider = providers.findById(session.getProviderId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
    OrganizationEntity org = orgs.findById(session.getOrgId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

    OidcEndpoints endpoints = resolveOidcEndpoints(provider);
    OidcTokenResponse tokenResponse = exchangeCode(endpoints.tokenUrl(), provider, session, code.trim());
    Claims claims = parseOidcIdToken(tokenResponse.idToken(), provider, session, endpoints.issuer());

    String email = resolveEmail(claims);
    String subject = claims.getSubject();
    if (email == null || email.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token missing email claim");
    }
    List<String> groups = resolveGroups(claims);
    String orgRole = resolveOrgRole(groups, claims.get("roles"));

    UserEntity user = upsertUser(org.getId(), provider.getId(), subject, email, orgRole);
    OrganizationMemberEntity membership = organizations.requireMembership(user, org.getId());
    List<String> roles = parseRoles(user.getRoles());
    List<String> orgRoles = List.of(membership.getRole());
    String token = jwt.issueToken(user.getId(), user.getEmail(), roles, user.isMfaEnabled(), org.getId(), orgRoles);

    sessions.delete(session);
    return new SsoLoginResponse(token, roles, org.getId(), org.getSlug(), orgRoles, user.getId(), user.getEmail());
  }

  public SsoStartResponse startSaml(Long orgId, String orgSlug, Long providerId, String redirectOverride, boolean redirect) {
    OrganizationEntity org = resolveOrg(orgId, orgSlug);
    IdentityProviderEntity provider = resolveProvider(org.getId(), providerId, "SAML");
    if (provider == null || !provider.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SAML provider not configured");
    }
    String ssoUrl = provider.getSsoUrl();
    if (ssoUrl == null || ssoUrl.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML provider missing SSO URL");
    }
    String acsUrl = resolveRedirectUri(provider, redirectOverride, baseUrl + "/api/v1/auth/sso/saml/acs");

    String state = randomToken();
    String requestId = "_" + UUID.randomUUID();
    String authnRequest = buildAuthnRequestXml(requestId, acsUrl, ssoUrl);
    String samlRequest = deflateBase64(authnRequest);

    SsoLoginSessionEntity session = new SsoLoginSessionEntity();
    session.setId(UUID.randomUUID().toString().replace("-", ""));
    session.setOrgId(org.getId());
    session.setProviderId(provider.getId());
    session.setFlowType("SAML");
    session.setState(state);
    session.setRequestId(requestId);
    session.setRedirectUri(acsUrl);
    session.setCreatedAt(Instant.now());
    session.setExpiresAt(Instant.now().plus(sessionTtlMinutes, ChronoUnit.MINUTES));
    sessions.save(session);

    String redirectUrl = ssoUrl + (ssoUrl.contains("?") ? "&" : "?") +
        "SAMLRequest=" + urlEncode(samlRequest) +
        "&RelayState=" + urlEncode(state);

    return new SsoStartResponse(redirectUrl, state, org.getId(), org.getSlug(), provider.getProviderType(), redirect);
  }

  public SsoLoginResponse handleSamlResponse(String samlResponse, String relayState) {
    if (samlResponse == null || samlResponse.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAMLResponse required");
    }
    if (relayState == null || relayState.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RelayState required");
    }
    SsoLoginSessionEntity session = sessions.findByState(relayState.trim()).orElse(null);
    if (session == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid relay state");
    }
    if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
      sessions.delete(session);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSO session expired");
    }
    IdentityProviderEntity provider = providers.findById(session.getProviderId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
    OrganizationEntity org = orgs.findById(session.getOrgId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

    SamlAssertion assertion = parseAndValidateSaml(samlResponse, provider, session);
    if (assertion.email == null || assertion.email.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML assertion missing email");
    }
    String orgRole = resolveOrgRole(assertion.groups, assertion.roles);

    UserEntity user = upsertUser(org.getId(), provider.getId(), assertion.subject, assertion.email, orgRole);
    OrganizationMemberEntity membership = organizations.requireMembership(user, org.getId());
    List<String> roles = parseRoles(user.getRoles());
    List<String> orgRoles = List.of(membership.getRole());
    String token = jwt.issueToken(user.getId(), user.getEmail(), roles, user.isMfaEnabled(), org.getId(), orgRoles);

    sessions.delete(session);
    return new SsoLoginResponse(token, roles, org.getId(), org.getSlug(), orgRoles, user.getId(), user.getEmail());
  }

  private OrganizationEntity resolveOrg(Long orgId, String orgSlug) {
    if (orgId != null) {
      return orgs.findById(orgId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }
    if (orgSlug != null && !orgSlug.isBlank()) {
      return orgs.findBySlug(orgSlug.trim()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orgId or orgSlug required");
  }

  private IdentityProviderEntity resolveProvider(Long orgId, Long providerId, String type) {
    if (providerId != null) {
      IdentityProviderEntity provider = providers.findById(providerId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
      if (!provider.getOrgId().equals(orgId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider does not belong to org");
      }
      if (type != null && !type.equalsIgnoreCase(provider.getProviderType())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider type mismatch");
      }
      return provider;
    }
    List<IdentityProviderEntity> configured = providers.findByOrgIdAndProviderTypeAndEnabledTrue(orgId, type.toUpperCase(Locale.US));
    if (configured.isEmpty()) {
      return null;
    }
    if (configured.size() > 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple providers configured; specify providerId");
    }
    return configured.get(0);
  }

  private String resolveRedirectUri(IdentityProviderEntity provider, String override, String fallback) {
    if (override != null && !override.isBlank()) {
      return override.trim();
    }
    if (provider.getRedirectUrl() != null && !provider.getRedirectUrl().isBlank()) {
      return provider.getRedirectUrl().trim();
    }
    return fallback;
  }

  private OidcEndpoints resolveOidcEndpoints(IdentityProviderEntity provider) {
    String authUrl = provider.getAuthorizationUrl();
    String tokenUrl = provider.getTokenUrl();
    String jwksUrl = provider.getJwksUrl();
    String issuer = provider.getIssuer();
    if ((authUrl == null || authUrl.isBlank() || tokenUrl == null || tokenUrl.isBlank()) &&
        provider.getMetadataUrl() != null && !provider.getMetadataUrl().isBlank()) {
      Map<String, Object> discovery = fetchJson(provider.getMetadataUrl());
      if (issuer == null) issuer = asString(discovery.get("issuer"));
      if (authUrl == null) authUrl = asString(discovery.get("authorization_endpoint"));
      if (tokenUrl == null) tokenUrl = asString(discovery.get("token_endpoint"));
      if (jwksUrl == null) jwksUrl = asString(discovery.get("jwks_uri"));
    }
    if (authUrl == null || authUrl.isBlank() || tokenUrl == null || tokenUrl.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC provider missing authorization/token URL");
    }
    return new OidcEndpoints(authUrl, tokenUrl, jwksUrl, issuer);
  }

  private OidcTokenResponse exchangeCode(String tokenUrl, IdentityProviderEntity provider, SsoLoginSessionEntity session, String code) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("code", code);
    body.add("redirect_uri", session.getRedirectUri());
    body.add("client_id", provider.getClientId());
    if (provider.getClientSecret() != null && !provider.getClientSecret().isBlank()) {
      body.add("client_secret", provider.getClientSecret());
    }
    if (session.getCodeVerifier() != null && !session.getCodeVerifier().isBlank()) {
      body.add("code_verifier", session.getCodeVerifier());
    }
    ResponseEntity<Map> res = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(body, headers), Map.class);
    if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token exchange failed");
    }
    Map<String, Object> payload = res.getBody();
    String idToken = asString(payload.get("id_token"));
    if (idToken == null || idToken.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC response missing id_token");
    }
    return new OidcTokenResponse(idToken,
        asString(payload.get("access_token")),
        asString(payload.get("token_type")),
        asString(payload.get("refresh_token")),
        asString(payload.get("scope")),
        asLong(payload.get("expires_in")));
  }

  Claims parseOidcIdToken(String idToken, IdentityProviderEntity provider, SsoLoginSessionEntity session, String issuerOverride) {
    Map<String, Object> header = parseJwtHeader(idToken);
    String keyId = asString(header.get("kid"));
    String alg = asString(header.get("alg"));
    PublicKey key = resolveOidcKey(provider, keyId, alg);
    if (key == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC provider missing verification key");
    }
    Claims claims;
    try {
      claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(idToken).getPayload();
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token expired");
    } catch (io.jsonwebtoken.PrematureJwtException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token not yet valid");
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OIDC token");
    }
    validateOidcTemporalClaims(claims);
    String issuer = provider.getIssuer();
    if ((issuer == null || issuer.isBlank()) && issuerOverride != null && !issuerOverride.isBlank()) {
      issuer = issuerOverride;
    }
    if (issuer != null && !issuer.isBlank()) {
      if (!issuer.equals(claims.getIssuer())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC issuer mismatch");
      }
    }
    if (provider.getClientId() != null) {
      if (!audMatches(provider.getClientId(), claims.getAudience(), claims.get("aud"))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC audience mismatch");
      }
    }
    if (session.getNonce() != null) {
      Object nonce = claims.get("nonce");
      if (nonce == null || !session.getNonce().equals(String.valueOf(nonce))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC nonce mismatch");
      }
    }
    return claims;
  }

  private void validateOidcTemporalClaims(Claims claims) {
    Instant now = Instant.now();
    Date exp = claims.getExpiration();
    if (exp == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token missing expiry");
    }
    if (!now.isBefore(exp.toInstant().plusSeconds(OIDC_CLOCK_SKEW_SECONDS))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token expired");
    }

    Date nbf = claims.getNotBefore();
    if (nbf != null && now.isBefore(nbf.toInstant().minusSeconds(OIDC_CLOCK_SKEW_SECONDS))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token not yet valid");
    }

    Date iat = claims.getIssuedAt();
    if (iat != null && iat.toInstant().isAfter(now.plusSeconds(OIDC_CLOCK_SKEW_SECONDS))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token issued-at is in the future");
    }
    if (iat != null && !iat.toInstant().isBefore(exp.toInstant())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC token issued-at/expiry window invalid");
    }
  }

  private Map<String, Object> parseJwtHeader(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        throw new IllegalArgumentException("Malformed JWT");
      }
      byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
      return mapper.readValue(decoded, Map.class);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OIDC token header");
    }
  }

  PublicKey resolveOidcKey(IdentityProviderEntity provider, String keyId, String alg) {
    if (provider.getX509Cert() != null && !provider.getX509Cert().isBlank()) {
      try {
        return parseCertificate(provider.getX509Cert());
      } catch (ResponseStatusException ignored) {
        // Fall through to JWKS lookup when certificate is malformed.
      }
    }
    String jwksUrl = provider.getJwksUrl();
    if ((jwksUrl == null || jwksUrl.isBlank()) && provider.getMetadataUrl() != null) {
      Map<String, Object> discovery = fetchJson(provider.getMetadataUrl());
      jwksUrl = asString(discovery.get("jwks_uri"));
    }
    if (jwksUrl == null || jwksUrl.isBlank()) {
      return null;
    }
    Map<String, Object> jwks = fetchJson(jwksUrl);
    Object keys = jwks.get("keys");
    if (!(keys instanceof List<?> list) || list.isEmpty()) {
      return null;
    }
    PublicKey fallback = null;
    for (Object entry : list) {
      if (!(entry instanceof Map<?, ?> map)) {
        continue;
      }
      String kty = asString(map.get("kty"));
      if (kty == null || !kty.equalsIgnoreCase("RSA")) {
        continue;
      }
      String use = asString(map.get("use"));
      if (use != null && !use.isBlank() && !"sig".equalsIgnoreCase(use)) {
        continue;
      }
      String keyAlg = asString(map.get("alg"));
      if (alg != null && !alg.isBlank() && keyAlg != null && !keyAlg.isBlank() && !alg.equalsIgnoreCase(keyAlg)) {
        continue;
      }

      PublicKey parsed = parseJwksRsaKey(map);
      if (parsed == null) {
        continue;
      }

      String candidateKid = asString(map.get("kid"));
      if (keyId != null && !keyId.isBlank()) {
        if (keyId.equals(candidateKid)) {
          return parsed;
        }
      } else if (fallback == null) {
        fallback = parsed;
      }
    }
    if (keyId != null && !keyId.isBlank()) {
      return null;
    }
    return fallback;
  }

  private PublicKey parseJwksRsaKey(Map<?, ?> map) {
    Object x5cObj = map.get("x5c");
    if (x5cObj instanceof List<?> x5cList && !x5cList.isEmpty()) {
      String cert = asString(x5cList.get(0));
      if (cert != null && !cert.isBlank()) {
        try {
          return parseCertificate("-----BEGIN CERTIFICATE-----\n" + cert + "\n-----END CERTIFICATE-----");
        } catch (ResponseStatusException ignored) {
          // Fall back to modulus/exponent if present.
        }
      }
    }

    String n = asString(map.get("n"));
    String e = asString(map.get("e"));
    if (n == null || e == null || n.isBlank() || e.isBlank()) {
      return null;
    }
    try {
      byte[] nBytes = Base64.getUrlDecoder().decode(n);
      byte[] eBytes = Base64.getUrlDecoder().decode(e);
      RSAPublicKeySpec spec = new RSAPublicKeySpec(new java.math.BigInteger(1, nBytes), new java.math.BigInteger(1, eBytes));
      return KeyFactory.getInstance("RSA").generatePublic(spec);
    } catch (Exception ignored) {
      return null;
    }
  }

  private UserEntity upsertUser(Long orgId, Long providerId, String subject, String email, String role) {
    UserEntity user = null;
    if (subject != null && providerId != null) {
      FederatedIdentityEntity existing = federated.findByProviderIdAndExternalSubject(providerId, subject).orElse(null);
      if (existing != null) {
        user = users.findById(existing.getUserId()).orElse(null);
      }
    }
    if (user == null && email != null) {
      user = users.findByEmail(email.toLowerCase(Locale.US).trim()).orElse(null);
    }
    if (user == null) {
      user = new UserEntity();
      user.setEmail(email.toLowerCase(Locale.US).trim());
      user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
      user.setRoles("USER");
      user.setStatus("ACTIVE");
      user = users.save(user);
    }

    if (user.getStatus() == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
      user.setStatus("ACTIVE");
      user.setDisabledAt(null);
    }
    user.setLastLoginAt(Instant.now());
    if (user.getPrimaryOrgId() == null) {
      user.setPrimaryOrgId(orgId);
    }
    users.save(user);

    if (subject != null && providerId != null) {
      FederatedIdentityEntity identity = federated.findByProviderIdAndExternalSubject(providerId, subject).orElse(null);
      if (identity == null) {
        identity = new FederatedIdentityEntity();
        identity.setOrgId(orgId);
        identity.setProviderId(providerId);
        identity.setExternalSubject(subject);
        identity.setEmail(user.getEmail());
        identity.setUserId(user.getId());
        identity.setCreatedAt(Instant.now());
      }
      identity.setUpdatedAt(Instant.now());
      federated.save(identity);
    }

    OrganizationMemberEntity membership = members.findByOrgIdAndUserId(orgId, user.getId()).orElse(null);
    if (membership == null) {
      membership = new OrganizationMemberEntity();
      membership.setOrgId(orgId);
      membership.setUserId(user.getId());
      membership.setRole(organizations.normalizeOrgRole(role));
      membership.setStatus("ACTIVE");
      membership.setCreatedAt(Instant.now());
      members.save(membership);
    } else if (role != null && !role.isBlank()) {
      membership.setRole(organizations.normalizeOrgRole(role));
      membership.setUpdatedAt(Instant.now());
      if (!"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
        membership.setStatus("ACTIVE");
      }
      members.save(membership);
    }

    return user;
  }

  private SamlAssertion parseAndValidateSaml(String samlResponse, IdentityProviderEntity provider, SsoLoginSessionEntity session) {
    try {
      byte[] decoded = Base64.getDecoder().decode(samlResponse);
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(decoded));

      Element response = (Element) doc.getDocumentElement();
      if (response == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SAML response");
      }

      validateInResponseTo(response, session);

      String issuer = textContent(doc, "Issuer");
      if (provider.getIssuer() != null && !provider.getIssuer().isBlank()) {
        if (issuer == null || !provider.getIssuer().equals(issuer)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML issuer mismatch");
        }
      }

      Element assertion = (Element) doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Assertion").item(0);
      if (assertion == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML assertion missing");
      }
      validateConditions(assertion);
      validateAudience(assertion);

      if (provider.getX509Cert() == null || provider.getX509Cert().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML provider missing certificate");
      }
      validateSignature(doc, provider.getX509Cert());

      String nameId = null;
      NodeList nameIds = assertion.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "NameID");
      if (nameIds.getLength() > 0) {
        nameId = nameIds.item(0).getTextContent();
      }

      Map<String, List<String>> attributes = parseAttributes(assertion);
      String email = resolveEmail(attributes, nameId);
      List<String> groups = resolveGroupAttributes(attributes);
      List<String> roles = attributes.getOrDefault("roles", List.of());

      return new SamlAssertion(nameId, email, groups, roles);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse SAML response");
    }
  }

  private void validateInResponseTo(Element response, SsoLoginSessionEntity session) {
    if (session == null || session.getRequestId() == null || session.getRequestId().isBlank()) {
      return;
    }
    String inResponseTo = response.getAttribute("InResponseTo");
    if (inResponseTo == null || inResponseTo.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML InResponseTo missing");
    }
    if (!session.getRequestId().equals(inResponseTo)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML InResponseTo mismatch");
    }
  }

  private void validateSignature(Document doc, String certPem) throws Exception {
    NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
    if (nl.getLength() == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML signature missing");
    }
    XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
    DOMValidateContext ctx = new DOMValidateContext(new StaticKeySelector(parseCertificate(certPem)), nl.item(0));
    XMLSignature sig = factory.unmarshalXMLSignature(ctx);
    if (!sig.validate(ctx)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SAML signature");
    }
  }

  private void validateConditions(Element assertion) {
    NodeList conditions = assertion.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Conditions");
    if (conditions.getLength() == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML conditions missing");
    }
    Element cond = (Element) conditions.item(0);
    String notBefore = cond.getAttribute("NotBefore");
    String notOnOrAfter = cond.getAttribute("NotOnOrAfter");
    if ((notBefore == null || notBefore.isBlank()) && (notOnOrAfter == null || notOnOrAfter.isBlank())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML conditions missing validity window");
    }
    Instant now = Instant.now();
    Instant nb = null;
    Instant noa = null;
    try {
      if (notBefore != null && !notBefore.isBlank()) {
        nb = Instant.parse(notBefore);
      }
      if (notOnOrAfter != null && !notOnOrAfter.isBlank()) {
        noa = Instant.parse(notOnOrAfter);
      }
    } catch (DateTimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SAML conditions timestamp");
    }
    if (nb != null && noa != null && !nb.isBefore(noa)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML conditions validity window invalid");
    }
    if (nb != null && now.isBefore(nb.minusSeconds(SAML_CLOCK_SKEW_SECONDS))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML assertion not yet valid");
    }
    if (noa != null && !now.isBefore(noa.plusSeconds(SAML_CLOCK_SKEW_SECONDS))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML assertion expired");
    }
  }

  private void validateAudience(Element assertion) {
    NodeList audiences = assertion.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Audience");
    if (audiences.getLength() == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML audience missing");
    }
    boolean matched = false;
    for (int i = 0; i < audiences.getLength(); i++) {
      String val = audiences.item(i).getTextContent();
      if (entityId.equals(val)) {
        matched = true;
        break;
      }
    }
    if (!matched) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SAML audience mismatch");
    }
  }

  private Map<String, List<String>> parseAttributes(Element assertion) {
    Map<String, List<String>> attrs = new LinkedHashMap<>();
    NodeList attrNodes = assertion.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Attribute");
    for (int i = 0; i < attrNodes.getLength(); i++) {
      Element attr = (Element) attrNodes.item(i);
      String name = attr.getAttribute("Name");
      if (name == null || name.isBlank()) continue;
      NodeList values = attr.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AttributeValue");
      List<String> list = new ArrayList<>();
      for (int j = 0; j < values.getLength(); j++) {
        String v = values.item(j).getTextContent();
        if (v != null && !v.isBlank()) list.add(v.trim());
      }
      attrs.put(name, list);
    }
    return attrs;
  }

  private String resolveEmail(Map<String, List<String>> attributes, String nameId) {
    List<String> candidates = new ArrayList<>();
    candidates.addAll(attributes.getOrDefault("email", List.of()));
    candidates.addAll(attributes.getOrDefault("mail", List.of()));
    candidates.addAll(attributes.getOrDefault("EmailAddress", List.of()));
    candidates.addAll(attributes.getOrDefault("urn:oid:0.9.2342.19200300.100.1.3", List.of()));
    for (String candidate : candidates) {
      if (candidate != null && candidate.contains("@")) {
        return candidate.trim().toLowerCase(Locale.US);
      }
    }
    if (nameId != null && nameId.contains("@")) {
      return nameId.trim().toLowerCase(Locale.US);
    }
    return null;
  }

  private List<String> resolveGroupAttributes(Map<String, List<String>> attributes) {
    List<String> groups = new ArrayList<>();
    groups.addAll(attributes.getOrDefault("groups", List.of()));
    groups.addAll(attributes.getOrDefault("role", List.of()));
    groups.addAll(attributes.getOrDefault("roles", List.of()));
    return groups;
  }

  private String resolveOrgRole(List<String> groups, Object rolesClaim) {
    List<String> values = new ArrayList<>();
    if (groups != null) values.addAll(groups);
    if (rolesClaim instanceof List<?> list) {
      for (Object v : list) {
        if (v != null) values.add(String.valueOf(v));
      }
    } else if (rolesClaim != null) {
      values.add(String.valueOf(rolesClaim));
    }
    for (String value : values) {
      if (value == null) continue;
      String normalized = value.trim().toUpperCase(Locale.US);
      if (normalized.contains("OWNER")) return "OWNER";
      if (normalized.contains("ADMIN")) return "ADMIN";
      if (normalized.contains("READ")) return "READ_ONLY";
    }
    return "MEMBER";
  }

  private List<String> parseRoles(String roles) {
    if (roles == null || roles.isBlank()) {
      return List.of("USER");
    }
    return Arrays.stream(roles.split(","))
        .map(String::trim)
        .filter(r -> !r.isEmpty())
        .map(r -> r.toUpperCase(Locale.US))
        .toList();
  }

  private List<String> resolveGroups(Claims claims) {
    Object groups = claims.get("groups");
    if (groups instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object v : list) out.add(String.valueOf(v));
      return out;
    }
    if (groups != null) return List.of(String.valueOf(groups));
    return List.of();
  }

  private String resolveEmail(Claims claims) {
    Object email = claims.get("email");
    if (email != null) return String.valueOf(email).trim().toLowerCase(Locale.US);
    Object upn = claims.get("upn");
    if (upn != null) return String.valueOf(upn).trim().toLowerCase(Locale.US);
    Object preferred = claims.get("preferred_username");
    if (preferred != null && String.valueOf(preferred).contains("@")) {
      return String.valueOf(preferred).trim().toLowerCase(Locale.US);
    }
    return null;
  }

  private boolean audMatches(String clientId, Set<String> audienceValues, Object audClaim) {
    if (audienceValues != null && !audienceValues.isEmpty()) {
      for (String value : audienceValues) {
        if (clientId.equals(String.valueOf(value))) {
          return true;
        }
      }
    }
    if (audClaim == null) return false;
    if (audClaim instanceof List<?> list) {
      for (Object v : list) {
        if (clientId.equals(String.valueOf(v))) return true;
      }
      return false;
    }
    if (audClaim instanceof Set<?> set) {
      for (Object v : set) {
        if (clientId.equals(String.valueOf(v))) return true;
      }
      return false;
    }
    return clientId.equals(String.valueOf(audClaim));
  }

  private PublicKey parseCertificate(String pem) {
    try {
      String cleaned = pem.replace("-----BEGIN CERTIFICATE-----", "")
          .replace("-----END CERTIFICATE-----", "")
          .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(cleaned);
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
      return cert.getPublicKey();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid certificate format");
    }
  }

  private String buildAuthnRequestXml(String requestId, String acsUrl, String destination) {
    String issueInstant = Instant.now().toString();
    return "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" " +
        "ID=\"" + requestId + "\" Version=\"2.0\" IssueInstant=\"" + issueInstant + "\" " +
        "Destination=\"" + destination + "\" " +
        "ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" " +
        "AssertionConsumerServiceURL=\"" + acsUrl + "\">" +
        "<saml:Issuer xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">" +
        entityId + "</saml:Issuer>" +
        "<samlp:NameIDPolicy AllowCreate=\"true\" " +
        "Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\"/>" +
        "</samlp:AuthnRequest>";
  }

  private String deflateBase64(String xml) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Deflater deflater = new Deflater(Deflater.DEFLATED, true);
      try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
        dos.write(xml.getBytes(StandardCharsets.UTF_8));
      }
      return Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build SAML request");
    }
  }

  Map<String, Object> fetchJson(String url) {
    ResponseEntity<String> res = restTemplate.getForEntity(url, String.class);
    if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to fetch metadata");
    }
    try {
      return mapper.readValue(res.getBody(), Map.class);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid metadata response");
    }
  }

  private String randomToken() {
    return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
  }

  private byte[] sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to hash value");
    }
  }

  private String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String urlEncode(String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return value;
    }
  }

  private String textContent(Document doc, String tag) {
    NodeList nodes = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", tag);
    if (nodes.getLength() == 0) return null;
    return nodes.item(0).getTextContent();
  }

  private String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private long asLong(Object value) {
    if (value == null) return 0;
    if (value instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (Exception e) {
      return 0;
    }
  }

  public record SsoStartResponse(String authorizationUrl, String state, Long orgId, String orgSlug,
                                 String providerType, boolean redirect) {}

  public record SsoLoginResponse(String token, List<String> roles, Long orgId, String orgSlug,
                                 List<String> orgRoles, Long userId, String email) {}

  private record OidcEndpoints(String authorizationUrl, String tokenUrl, String jwksUrl, String issuer) {}

  private record OidcTokenResponse(String idToken, String accessToken, String tokenType, String refreshToken,
                                   String scope, long expiresIn) {}

  private record SamlAssertion(String subject, String email, List<String> groups, List<String> roles) {}

  private static class StaticKeySelector extends KeySelector {
    private final PublicKey key;

    StaticKeySelector(PublicKey key) {
      this.key = key;
    }

    @Override
    public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
        throws KeySelectorException {
      return () -> key;
    }
  }
}

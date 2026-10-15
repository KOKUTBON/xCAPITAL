package com.alphamath.auth.web;

import com.alphamath.auth.org.*;
import com.alphamath.auth.security.AuthContextService;
import com.alphamath.auth.user.UserEntity;
import com.alphamath.auth.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/orgs")
public class OrganizationController {
  private final AuthContextService auth;
  private final OrganizationService organizations;
  private final ProvisioningService provisioning;
  private final OrganizationRepository orgRepo;
  private final OrganizationMemberRepository memberRepo;
  private final UserRepository users;

  public OrganizationController(AuthContextService auth,
                                OrganizationService organizations,
                                ProvisioningService provisioning,
                                OrganizationRepository orgRepo,
                                OrganizationMemberRepository memberRepo,
                                UserRepository users) {
    this.auth = auth;
    this.organizations = organizations;
    this.provisioning = provisioning;
    this.orgRepo = orgRepo;
    this.memberRepo = memberRepo;
    this.users = users;
  }

  @GetMapping
  public List<OrgSummary> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    UserEntity user = auth.requireUser(authHeader);
    List<OrganizationMemberEntity> memberships = organizations.membershipsForUser(user.getId());
    List<OrgSummary> response = new ArrayList<>();
    for (OrganizationMemberEntity membership : memberships) {
      OrganizationEntity org = orgRepo.findById(membership.getOrgId()).orElse(null);
      if (org == null) continue;
      response.add(OrgSummary.from(org, membership));
    }
    return response;
  }

  @PostMapping
  public OrgSummary create(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                           @Valid @RequestBody OrgCreateRequest request) {
    UserEntity user = auth.requireUser(authHeader);
    OrganizationEntity org = organizations.createOrganization(user, request.name);
    OrganizationMemberEntity membership = memberRepo.findByOrgIdAndUserId(org.getId(), user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Owner membership missing"));
    return OrgSummary.from(org, membership);
  }

  @GetMapping("/{orgId}")
  public OrgSummary get(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                        @PathVariable Long orgId) {
    UserEntity user = auth.requireUser(authHeader);
    OrganizationMemberEntity membership = organizations.requireMembership(user, orgId);
    OrganizationEntity org = orgRepo.findById(orgId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    return OrgSummary.from(org, membership);
  }

  @GetMapping("/{orgId}/members")
  public List<OrgMemberView> members(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                     @PathVariable Long orgId) {
    UserEntity user = auth.requireUser(authHeader);
    organizations.requireOrgRole(user, orgId, OrganizationService.ROLE_ADMIN);
    List<OrganizationMemberEntity> memberships = organizations.membersForOrg(orgId);
    List<OrgMemberView> response = new ArrayList<>();
    for (OrganizationMemberEntity membership : memberships) {
      UserEntity memberUser = users.findById(membership.getUserId()).orElse(null);
      response.add(OrgMemberView.from(membership, memberUser));
    }
    return response;
  }

  @PostMapping("/{orgId}/members/{userId}/role")
  public OrgMemberView updateRole(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                  @PathVariable Long orgId,
                                  @PathVariable Long userId,
                                  @Valid @RequestBody OrgRoleUpdateRequest request) {
    UserEntity actor = auth.requireUser(authHeader);
    OrganizationMemberEntity membership = organizations.updateMemberRole(actor, orgId, userId, request.role);
    UserEntity memberUser = users.findById(userId).orElse(null);
    return OrgMemberView.from(membership, memberUser);
  }

  @PostMapping("/{orgId}/invites")
  public OrgInviteResponse invite(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                  @PathVariable Long orgId,
                                  @Valid @RequestBody OrgInviteRequest request) {
    UserEntity actor = auth.requireUser(authHeader);
    long days = request.expiresInDays == null || request.expiresInDays <= 0 ? 7 : request.expiresInDays;
    Instant expiresAt = Instant.now().plus(days, ChronoUnit.DAYS);
    OrganizationInviteEntity invite = organizations.invite(actor, orgId, request.email, request.role, expiresAt);
    return OrgInviteResponse.from(invite);
  }

  @PostMapping("/invites/{token}/accept")
  public OrgMemberView accept(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                              @PathVariable String token) {
    UserEntity user = auth.requireUser(authHeader);
    OrganizationMemberEntity membership = organizations.acceptInvite(user, token);
    UserEntity memberUser = users.findById(user.getId()).orElse(null);
    return OrgMemberView.from(membership, memberUser);
  }

  @GetMapping("/{orgId}/sso")
  public List<IdentityProviderResponse> listProviders(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @PathVariable Long orgId) {
    UserEntity user = auth.requireUser(authHeader);
    organizations.requireOrgRole(user, orgId, OrganizationService.ROLE_ADMIN);
    return provisioning.listProviders(orgId).stream().map(IdentityProviderResponse::from).toList();
  }

  @PostMapping("/{orgId}/sso")
  public IdentityProviderResponse upsertProvider(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @PathVariable Long orgId,
      @Valid @RequestBody IdentityProviderRequest request) {
    UserEntity user = auth.requireUser(authHeader);
    organizations.requireOrgRole(user, orgId, OrganizationService.ROLE_ADMIN);
    IdentityProviderEntity entity = new IdentityProviderEntity();
    entity.setProviderType(request.providerType);
    entity.setIssuer(request.issuer);
    entity.setSsoUrl(request.ssoUrl);
    entity.setMetadataUrl(request.metadataUrl);
    entity.setAuthorizationUrl(request.authorizationUrl);
    entity.setTokenUrl(request.tokenUrl);
    entity.setJwksUrl(request.jwksUrl);
    entity.setScopes(request.scopes);
    entity.setRedirectUrl(request.redirectUrl);
    entity.setClientId(request.clientId);
    entity.setClientSecret(request.clientSecret);
    entity.setX509Cert(request.x509Cert);
    entity.setEnabled(request.enabled == null || request.enabled);
    return IdentityProviderResponse.from(provisioning.upsertProvider(orgId, entity));
  }

  @DeleteMapping("/{orgId}/sso/{providerId}")
  public void deleteProvider(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                             @PathVariable Long orgId,
                             @PathVariable Long providerId) {
    UserEntity user = auth.requireUser(authHeader);
    organizations.requireOrgRole(user, orgId, OrganizationService.ROLE_ADMIN);
    provisioning.deleteProvider(orgId, providerId);
  }

  @GetMapping("/{orgId}/scim")
  public ScimConfigResponse scimConfig(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                       @PathVariable Long orgId) {
    UserEntity user = auth.requireUser(authHeader);
    organizations.requireOrgRole(user, orgId, OrganizationService.ROLE_ADMIN);
    ScimConfigEntity config = provisioning.getScimConfig(orgId);
    return ScimConfigResponse.from(config, null);
  }

  @PostMapping("/{orgId}/scim")
  public ScimConfigResponse updateScim(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                       @PathVariable Long orgId,
                                       @Valid @RequestBody ScimConfigRequest request) {
    UserEntity user = auth.requireUser(authHeader);
    organizations.requireOrgRole(user, orgId, OrganizationService.ROLE_ADMIN);
    ScimConfigEntity config = provisioning.updateScimConfig(orgId, request.baseUrl, request.enabled == null || request.enabled);
    return ScimConfigResponse.from(config, null);
  }

  @PostMapping("/{orgId}/scim/rotate")
  public ScimConfigResponse rotateScim(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                       @PathVariable Long orgId,
                                       @Valid @RequestBody ScimConfigRequest request) {
    UserEntity user = auth.requireUser(authHeader);
    organizations.requireOrgRole(user, orgId, OrganizationService.ROLE_ADMIN);
    ProvisioningService.ScimToken token = provisioning.rotateScimToken(orgId, request.baseUrl, request.enabled == null || request.enabled);
    ScimConfigEntity config = provisioning.getScimConfig(orgId);
    return ScimConfigResponse.from(config, token.token());
  }

  @Data
  static class OrgCreateRequest {
    @NotBlank
    public String name;
  }

  @Data
  static class OrgRoleUpdateRequest {
    @NotBlank
    public String role;
  }

  @Data
  static class OrgInviteRequest {
    @Email @NotBlank
    public String email;
    public String role;
    public Long expiresInDays;
  }

  @Data
  static class IdentityProviderRequest {
    @NotBlank
    public String providerType;
    public String issuer;
    public String ssoUrl;
    public String metadataUrl;
    public String authorizationUrl;
    public String tokenUrl;
    public String jwksUrl;
    public String scopes;
    public String redirectUrl;
    public String clientId;
    public String clientSecret;
    public String x509Cert;
    public Boolean enabled;
  }

  @Data
  static class ScimConfigRequest {
    public String baseUrl;
    public Boolean enabled;
  }

  @Data
  static class OrgSummary {
    public Long id;
    public String name;
    public String slug;
    public String status;
    public String role;
    public String memberStatus;
    public Instant createdAt;

    static OrgSummary from(OrganizationEntity org, OrganizationMemberEntity membership) {
      OrgSummary summary = new OrgSummary();
      summary.id = org.getId();
      summary.name = org.getName();
      summary.slug = org.getSlug();
      summary.status = org.getStatus();
      summary.role = membership.getRole();
      summary.memberStatus = membership.getStatus();
      summary.createdAt = org.getCreatedAt();
      return summary;
    }
  }

  @Data
  static class OrgMemberView {
    public Long userId;
    public String email;
    public String role;
    public String status;
    public Instant createdAt;

    static OrgMemberView from(OrganizationMemberEntity membership, UserEntity user) {
      OrgMemberView view = new OrgMemberView();
      view.userId = membership.getUserId();
      view.role = membership.getRole();
      view.status = membership.getStatus();
      view.createdAt = membership.getCreatedAt();
      view.email = user == null ? null : user.getEmail();
      return view;
    }
  }

  @Data
  static class OrgInviteResponse {
    public Long id;
    public Long orgId;
    public String email;
    public String role;
    public String status;
    public String token;
    public Instant expiresAt;
    public Instant createdAt;

    static OrgInviteResponse from(OrganizationInviteEntity invite) {
      OrgInviteResponse response = new OrgInviteResponse();
      response.id = invite.getId();
      response.orgId = invite.getOrgId();
      response.email = invite.getEmail();
      response.role = invite.getRole();
      response.status = invite.getStatus();
      response.token = invite.getToken();
      response.expiresAt = invite.getExpiresAt();
      response.createdAt = invite.getCreatedAt();
      return response;
    }
  }

  @Data
  static class IdentityProviderResponse {
    public Long id;
    public String providerType;
    public String issuer;
    public String ssoUrl;
    public String metadataUrl;
    public String authorizationUrl;
    public String tokenUrl;
    public String jwksUrl;
    public String scopes;
    public String redirectUrl;
    public String clientId;
    public boolean enabled;
    public boolean clientSecretSet;
    public boolean certificateSet;

    static IdentityProviderResponse from(IdentityProviderEntity provider) {
      IdentityProviderResponse response = new IdentityProviderResponse();
      response.id = provider.getId();
      response.providerType = provider.getProviderType();
      response.issuer = provider.getIssuer();
      response.ssoUrl = provider.getSsoUrl();
      response.metadataUrl = provider.getMetadataUrl();
      response.authorizationUrl = provider.getAuthorizationUrl();
      response.tokenUrl = provider.getTokenUrl();
      response.jwksUrl = provider.getJwksUrl();
      response.scopes = provider.getScopes();
      response.redirectUrl = provider.getRedirectUrl();
      response.clientId = provider.getClientId();
      response.enabled = provider.isEnabled();
      response.clientSecretSet = provider.getClientSecret() != null && !provider.getClientSecret().isBlank();
      response.certificateSet = provider.getX509Cert() != null && !provider.getX509Cert().isBlank();
      return response;
    }
  }

  @Data
  static class ScimConfigResponse {
    public Long id;
    public String baseUrl;
    public boolean enabled;
    public Instant lastRotatedAt;
    public String token;

    static ScimConfigResponse from(ScimConfigEntity config, String token) {
      ScimConfigResponse response = new ScimConfigResponse();
      if (config != null) {
        response.id = config.getId();
        response.baseUrl = config.getBaseUrl();
        response.enabled = config.isEnabled();
        response.lastRotatedAt = config.getLastRotatedAt();
      }
      response.token = token;
      return response;
    }
  }
}

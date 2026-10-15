package com.alphamath.auth.web;

import com.alphamath.auth.mfa.TotpService;
import com.alphamath.auth.org.OrganizationEntity;
import com.alphamath.auth.org.OrganizationMemberEntity;
import com.alphamath.auth.org.OrganizationService;
import com.alphamath.auth.security.AuthBootstrapProperties;
import com.alphamath.auth.security.AuthContextService;
import com.alphamath.auth.security.JwtService;
import com.alphamath.auth.user.UserEntity;
import com.alphamath.auth.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final UserRepository users;
  private final JwtService jwt;
  private final AuthBootstrapProperties bootstrap;
  private final TotpService totp;
  private final AuthContextService authContext;
  private final OrganizationService organizations;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public AuthController(UserRepository users,
                        JwtService jwt,
                        AuthBootstrapProperties bootstrap,
                        TotpService totp,
                        AuthContextService authContext,
                        OrganizationService organizations) {
    this.users = users;
    this.jwt = jwt;
    this.bootstrap = bootstrap;
    this.totp = totp;
    this.authContext = authContext;
    this.organizations = organizations;
  }

  @PostMapping("/register")
  public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
    if (users.existsByEmail(req.email)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }

    UserEntity u = new UserEntity();
    u.setEmail(req.email.toLowerCase().trim());
    u.setPasswordHash(encoder.encode(req.password));
    u.setStatus("ACTIVE");
    boolean bootstrapAdmin = bootstrap != null && bootstrap.isBootstrapAdmin(u.getEmail());
    u.setRoles(bootstrapAdmin ? "USER,ADMIN" : "USER");
    u = users.save(u);

    List<String> roles = parseRoles(u.getRoles());
    OrganizationEntity org = organizations.createOrganization(u, req.organizationName);
    OrganizationMemberEntity membership = organizations.requireMembership(u, org.getId());
    String token = jwt.issueToken(u.getId(), u.getEmail(), roles, u.isMfaEnabled(), org.getId(), List.of(membership.getRole()));
    return new TokenResponse(token, roles, u.isMfaEnabled(), false, null, null, org.getId(), org.getSlug(), List.of(membership.getRole()));
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest req) {
    UserEntity u = users.findByEmail(req.email.toLowerCase().trim())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

    if (u.getStatus() != null && !"ACTIVE".equalsIgnoreCase(u.getStatus())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled");
    }

    if (!encoder.matches(req.password, u.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    if (bootstrap != null && bootstrap.isBootstrapAdmin(u.getEmail()) && !hasRole(u.getRoles(), "ADMIN")) {
      List<String> upgraded = new java.util.ArrayList<>(parseRoles(u.getRoles()));
      upgraded.add("ADMIN");
      u.setRoles(normalizeRoles(upgraded));
      users.save(u);
    }

    List<String> roles = parseRoles(u.getRoles());
    OrganizationEntity org = organizations.resolveOrganization(u, req.orgId, req.orgSlug);
    OrganizationMemberEntity membership = organizations.requireMembership(u, org.getId());
    List<String> orgRoles = List.of(membership.getRole());
    if (u.isMfaEnabled()) {
      JwtService.MfaChallenge challenge = jwt.issueMfaChallenge(u.getId(), u.getEmail(), org.getId(), orgRoles);
      return new TokenResponse(null, roles, true, true, challenge.getToken(), challenge.getExpiresAt(), org.getId(), org.getSlug(), orgRoles);
    }
    u.setLastLoginAt(Instant.now());
    users.save(u);
    String token = jwt.issueToken(u.getId(), u.getEmail(), roles, false, org.getId(), orgRoles);
    return new TokenResponse(token, roles, false, false, null, null, org.getId(), org.getSlug(), orgRoles);
  }

  @GetMapping("/profile")
  public UserProfile profile(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
    UserEntity user = authContext.requireUser(auth);
    UserProfile profile = new UserProfile();
    profile.id = user.getId();
    profile.email = user.getEmail();
    profile.roles = parseRoles(user.getRoles());
    profile.primaryOrgId = user.getPrimaryOrgId();
    profile.status = user.getStatus();
    profile.disabledAt = user.getDisabledAt();
    profile.mfaEnabled = user.isMfaEnabled();
    profile.mfaMethod = user.getMfaMethod();
    profile.mfaEnrolledAt = user.getMfaEnrolledAt();
    profile.mfaVerifiedAt = user.getMfaVerifiedAt();
    profile.createdAt = user.getCreatedAt();
    return profile;
  }

  @PostMapping("/mfa/enroll")
  public MfaEnrollResponse enroll(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                  @Valid @RequestBody MfaEnrollRequest req) {
    UserEntity user = authContext.requireUser(auth);
    String method = req.method == null ? "TOTP" : req.method.trim().toUpperCase(Locale.US);
    if (!method.equals("TOTP")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported MFA method");
    }
    String secret = totp.generateSecret();
    user.setMfaMethod(method);
    user.setMfaSecret(totp.encryptSecret(secret));
    user.setMfaEnrolledAt(Instant.now());
    user.setMfaEnabled(false);
    user.setMfaVerifiedAt(null);
    users.save(user);

    MfaEnrollResponse response = new MfaEnrollResponse();
    response.method = method;
    response.enrolledAt = user.getMfaEnrolledAt();
    response.secretMasked = totp.maskSecret(secret);
    response.secret = secret;
    response.issuer = totp.issuer();
    response.otpauthUrl = totp.otpauthUrl(user.getEmail(), secret);
    response.note = "Scan the otpauth URL in an authenticator app, then verify with the next code.";
    return response;
  }

  @PostMapping("/mfa/verify")
  public MfaStatusResponse verify(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                  @Valid @RequestBody MfaVerifyRequest req) {
    UserEntity user;
    boolean viaChallenge = false;
    Long orgId = null;
    List<String> orgRoles = List.of();
    if (req.mfaToken != null && !req.mfaToken.isBlank()) {
      try {
        var claims = jwt.parseToken(req.mfaToken.trim());
        if (!jwt.isMfaChallenge(claims)) {
          throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid MFA token");
        }
        Object uidClaim = claims.get("uid");
        String uid = uidClaim == null ? "" : String.valueOf(uidClaim);
        Long id = Long.parseLong(uid);
        user = users.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        viaChallenge = true;
        Object orgClaim = claims.get("org_id");
        if (orgClaim != null) {
          orgId = Long.parseLong(String.valueOf(orgClaim));
        }
        Object orgRolesClaim = claims.get("org_roles");
        orgRoles = parseClaimRoles(orgRolesClaim);
      } catch (ResponseStatusException e) {
        throw e;
      } catch (Exception e) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid MFA token");
      }
    } else {
      user = authContext.requireUser(auth);
    }
    if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA not enrolled");
    }
    if (!totp.verifyCode(user.getMfaSecret(), req.code)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid MFA code");
    }
    user.setMfaEnabled(true);
    user.setMfaVerifiedAt(Instant.now());
    user.setLastLoginAt(Instant.now());
    users.save(user);
    MfaStatusResponse status = buildMfaStatus(user);
    if (!viaChallenge) {
      return status;
    }
    List<String> roles = parseRoles(user.getRoles());
    OrganizationEntity org;
    if (orgId != null) {
      org = organizations.resolveOrganization(user, orgId, null);
      OrganizationMemberEntity membership = organizations.requireMembership(user, org.getId());
      orgRoles = List.of(membership.getRole());
    } else {
      org = organizations.resolveOrganization(user, null, null);
      OrganizationMemberEntity membership = organizations.requireMembership(user, org.getId());
      orgRoles = List.of(membership.getRole());
    }
    String token = jwt.issueToken(user.getId(), user.getEmail(), roles, true, org.getId(), orgRoles);
    return new MfaStatusResponse(status, token, roles, false, org.getId(), org.getSlug(), orgRoles);
  }

  @PostMapping("/mfa/disable")
  public MfaStatusResponse disable(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
    UserEntity user = authContext.requireUser(auth);
    user.setMfaEnabled(false);
    user.setMfaVerifiedAt(null);
    user.setMfaSecret(null);
    user.setMfaMethod(null);
    user.setMfaEnrolledAt(null);
    users.save(user);
    return buildMfaStatus(user);
  }

  @PostMapping("/users/{id}/roles")
  public UserRoleResponse updateRoles(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                      @PathVariable Long id,
                                      @Valid @RequestBody RoleUpdateRequest req) {
    authContext.requireAdmin(auth);
    UserEntity target = users.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    String normalized = normalizeRoles(req.roles);
    target.setRoles(normalized);
    users.save(target);
    UserRoleResponse response = new UserRoleResponse();
    response.id = target.getId();
    response.email = target.getEmail();
    response.roles = parseRoles(target.getRoles());
    response.updatedAt = Instant.now();
    return response;
  }

  @Data
  static class RegisterRequest {
    @Email @NotBlank
    public String email;

    @NotBlank @Size(min = 8, max = 72)
    public String password;
    public String organizationName;
  }

  @Data
  static class LoginRequest {
    @Email @NotBlank
    public String email;

    @NotBlank @Size(min = 8, max = 72)
    public String password;
    public Long orgId;
    public String orgSlug;
  }

  @Data
  static class TokenResponse {
    public final String token;
    public final List<String> roles;
    public final boolean mfaEnabled;
    public final boolean mfaRequired;
    public final String mfaToken;
    public final Instant mfaTokenExpiresAt;
    public final Long orgId;
    public final String orgSlug;
    public final List<String> orgRoles;
  }

  @Data
  static class UserProfile {
    public Long id;
    public String email;
    public List<String> roles;
    public Long primaryOrgId;
    public String status;
    public Instant disabledAt;
    public boolean mfaEnabled;
    public String mfaMethod;
    public Instant mfaEnrolledAt;
    public Instant mfaVerifiedAt;
    public Instant createdAt;
  }

  @Data
  static class MfaEnrollRequest {
    public String method;
  }

  @Data
  static class MfaEnrollResponse {
    public String method;
    public String secretMasked;
    public String secret;
    public String issuer;
    public String otpauthUrl;
    public Instant enrolledAt;
    public String note;
  }

  @Data
  static class MfaVerifyRequest {
    @NotBlank
    public String code;
    public String mfaToken;
  }

  @Data
  static class MfaStatusResponse {
    public boolean mfaEnabled;
    public String mfaMethod;
    public Instant mfaEnrolledAt;
    public Instant mfaVerifiedAt;
    public String token;
    public List<String> roles;
    public boolean mfaRequired;
    public Long orgId;
    public String orgSlug;
    public List<String> orgRoles;

    public MfaStatusResponse() {}

    public MfaStatusResponse(MfaStatusResponse base, String token, List<String> roles, boolean mfaRequired,
                             Long orgId, String orgSlug, List<String> orgRoles) {
      this.mfaEnabled = base.mfaEnabled;
      this.mfaMethod = base.mfaMethod;
      this.mfaEnrolledAt = base.mfaEnrolledAt;
      this.mfaVerifiedAt = base.mfaVerifiedAt;
      this.token = token;
      this.roles = roles;
      this.mfaRequired = mfaRequired;
      this.orgId = orgId;
      this.orgSlug = orgSlug;
      this.orgRoles = orgRoles;
    }
  }

  @Data
  static class RoleUpdateRequest {
    @Size(min = 1, max = 10)
    public List<@NotBlank String> roles;
  }

  @Data
  static class UserRoleResponse {
    public Long id;
    public String email;
    public List<String> roles;
    public Instant updatedAt;
  }

  private List<String> parseRoles(String roles) {
    if (roles == null || roles.isBlank()) {
      return List.of("USER");
    }
    return java.util.Arrays.stream(roles.split(","))
        .map(String::trim)
        .filter(r -> !r.isEmpty())
        .map(r -> r.toUpperCase(Locale.US))
        .toList();
  }

  private boolean hasRole(String roles, String required) {
    if (required == null || required.isBlank()) {
      return true;
    }
    String needle = required.trim().toUpperCase(Locale.US);
    return parseRoles(roles).stream().anyMatch(r -> r.equals(needle));
  }

  private List<String> parseClaimRoles(Object claim) {
    if (claim == null) return List.of();
    if (claim instanceof List<?>) {
      List<?> values = (List<?>) claim;
      return values.stream().map(Object::toString).toList();
    }
    return List.of(claim.toString());
  }

  private MfaStatusResponse buildMfaStatus(UserEntity user) {
    MfaStatusResponse response = new MfaStatusResponse();
    response.mfaEnabled = user.isMfaEnabled();
    response.mfaMethod = user.getMfaMethod();
    response.mfaEnrolledAt = user.getMfaEnrolledAt();
    response.mfaVerifiedAt = user.getMfaVerifiedAt();
    return response;
  }

  private String normalizeRoles(List<String> roles) {
    return authContext.normalizeRoles(roles);
  }
}

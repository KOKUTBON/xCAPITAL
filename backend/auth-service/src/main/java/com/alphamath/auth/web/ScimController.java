package com.alphamath.auth.web;

import com.alphamath.auth.org.OrganizationMemberEntity;
import com.alphamath.auth.org.OrganizationMemberRepository;
import com.alphamath.auth.org.OrganizationService;
import com.alphamath.auth.scim.ScimAuthService;
import com.alphamath.auth.user.UserEntity;
import com.alphamath.auth.user.UserRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scim/v2")
public class ScimController {
  private final ScimAuthService scimAuth;
  private final UserRepository users;
  private final OrganizationMemberRepository members;
  private final OrganizationService organizations;

  public ScimController(ScimAuthService scimAuth,
                        UserRepository users,
                        OrganizationMemberRepository members,
                        OrganizationService organizations) {
    this.scimAuth = scimAuth;
    this.users = users;
    this.members = members;
    this.organizations = organizations;
  }

  @GetMapping("/ServiceProviderConfig")
  public Map<String, Object> serviceProvider(@RequestHeader("Authorization") String auth) {
    scimAuth.authenticate(auth);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
    out.put("patch", Map.of("supported", true));
    out.put("bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0));
    out.put("filter", Map.of("supported", true, "maxResults", 200));
    out.put("changePassword", Map.of("supported", false));
    out.put("sort", Map.of("supported", false));
    out.put("etag", Map.of("supported", false));
    return out;
  }

  @GetMapping("/Users")
  public Map<String, Object> listUsers(@RequestHeader("Authorization") String auth,
                                       @RequestParam(name = "filter", required = false) String filter,
                                       @RequestParam(name = "startIndex", required = false, defaultValue = "1") int startIndex,
                                       @RequestParam(name = "count", required = false, defaultValue = "50") int count) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    List<Map<String, Object>> resources = new ArrayList<>();

    if (filter != null && filter.contains("userName")) {
      String email = parseFilterValue(filter);
      if (email != null) {
        UserEntity user = users.findByEmail(email.toLowerCase(Locale.US)).orElse(null);
        if (user != null) {
          OrganizationMemberEntity membership = members.findByOrgIdAndUserId(principal.orgId(), user.getId()).orElse(null);
          if (membership != null) {
            resources.add(toScimUser(user, membership));
          }
        }
      }
    } else {
      List<OrganizationMemberEntity> memberships = members.findByOrgId(principal.orgId());
      int start = Math.max(0, startIndex - 1);
      int end = Math.min(memberships.size(), start + Math.max(1, count));
      for (int i = start; i < end; i++) {
        OrganizationMemberEntity membership = memberships.get(i);
        UserEntity user = users.findById(membership.getUserId()).orElse(null);
        if (user != null) {
          resources.add(toScimUser(user, membership));
        }
      }
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ListResponse"));
    out.put("totalResults", resources.size());
    out.put("startIndex", startIndex);
    out.put("itemsPerPage", resources.size());
    out.put("Resources", resources);
    return out;
  }

  @PostMapping("/Users")
  public Map<String, Object> createUser(@RequestHeader("Authorization") String auth,
                                        @Valid @RequestBody ScimUserPayload payload) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    String email = resolveEmail(payload);
    if (email == null || email.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userName/email required");
    }
    UserEntity user = users.findByEmail(email.toLowerCase(Locale.US)).orElse(null);
    if (user == null) {
      user = new UserEntity();
      user.setEmail(email.toLowerCase(Locale.US));
      user.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));
      user.setRoles("USER");
      user.setStatus(payload.active != null && !payload.active ? "DISABLED" : "ACTIVE");
      if (!"ACTIVE".equals(user.getStatus())) {
        user.setDisabledAt(Instant.now());
      }
      user = users.save(user);
    }

    OrganizationMemberEntity membership = upsertMembership(principal.orgId(), user, resolveRole(payload), payload.active);
    return toScimUser(user, membership);
  }

  @GetMapping("/Users/{id}")
  public Map<String, Object> getUser(@RequestHeader("Authorization") String auth,
                                     @PathVariable("id") Long id) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    UserEntity user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    OrganizationMemberEntity membership = members.findByOrgIdAndUserId(principal.orgId(), user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    return toScimUser(user, membership);
  }

  @PutMapping("/Users/{id}")
  public Map<String, Object> replaceUser(@RequestHeader("Authorization") String auth,
                                         @PathVariable("id") Long id,
                                         @Valid @RequestBody ScimUserPayload payload) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    UserEntity user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    String email = resolveEmail(payload);
    if (email != null && !email.isBlank() && !email.equalsIgnoreCase(user.getEmail())) {
      if (users.existsByEmail(email.toLowerCase(Locale.US))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
      }
      user.setEmail(email.toLowerCase(Locale.US));
    }
    applyActive(user, payload.active);
    users.save(user);
    OrganizationMemberEntity membership = upsertMembership(principal.orgId(), user, resolveRole(payload), payload.active);
    return toScimUser(user, membership);
  }

  @PatchMapping("/Users/{id}")
  public Map<String, Object> patchUser(@RequestHeader("Authorization") String auth,
                                       @PathVariable("id") Long id,
                                       @RequestBody ScimPatchRequest payload) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    UserEntity user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    OrganizationMemberEntity membership = members.findByOrgIdAndUserId(principal.orgId(), user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    String desiredRole = null;
    Boolean active = null;
    String email = null;
    if (payload != null && payload.Operations != null) {
      for (ScimPatchOperation op : payload.Operations) {
        String path = op.path == null ? "" : op.path.toLowerCase(Locale.US);
        if (path.contains("active")) {
          active = extractBoolean(op.value);
        }
        if (path.contains("userName".toLowerCase(Locale.US)) || path.contains("emails")) {
          String candidate = extractString(op.value);
          if (candidate != null && candidate.contains("@")) {
            email = candidate;
          }
        }
        if (path.contains("roles") || path.contains("groups")) {
          desiredRole = extractRole(op.value);
        }
      }
    }

    if (email != null && !email.equalsIgnoreCase(user.getEmail())) {
      if (users.existsByEmail(email.toLowerCase(Locale.US))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
      }
      user.setEmail(email.toLowerCase(Locale.US));
    }
    applyActive(user, active);
    users.save(user);

    if (desiredRole != null) {
      membership.setRole(organizations.normalizeOrgRole(desiredRole));
      membership.setUpdatedAt(Instant.now());
    }
    if (active != null) {
      membership.setStatus(active ? "ACTIVE" : "SUSPENDED");
    }
    members.save(membership);

    return toScimUser(user, membership);
  }

  @DeleteMapping("/Users/{id}")
  public void deleteUser(@RequestHeader("Authorization") String auth,
                         @PathVariable("id") Long id) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    UserEntity user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    OrganizationMemberEntity membership = members.findByOrgIdAndUserId(principal.orgId(), user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    user.setStatus("DISABLED");
    user.setDisabledAt(Instant.now());
    users.save(user);
    membership.setStatus("SUSPENDED");
    membership.setUpdatedAt(Instant.now());
    members.save(membership);
  }

  @GetMapping("/Groups")
  public Map<String, Object> listGroups(@RequestHeader("Authorization") String auth) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    List<Map<String, Object>> resources = new ArrayList<>();
    for (String role : List.of("OWNER", "ADMIN", "MEMBER", "READ_ONLY")) {
      resources.add(scimGroup(principal.orgId(), role));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ListResponse"));
    out.put("totalResults", resources.size());
    out.put("startIndex", 1);
    out.put("itemsPerPage", resources.size());
    out.put("Resources", resources);
    return out;
  }

  @GetMapping("/Groups/{id}")
  public Map<String, Object> getGroup(@RequestHeader("Authorization") String auth,
                                      @PathVariable("id") String id) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    return scimGroup(principal.orgId(), id);
  }

  @PatchMapping("/Groups/{id}")
  public Map<String, Object> patchGroup(@RequestHeader("Authorization") String auth,
                                        @PathVariable("id") String id,
                                        @RequestBody ScimPatchRequest payload) {
    ScimAuthService.ScimPrincipal principal = scimAuth.authenticate(auth);
    String role = organizations.normalizeOrgRole(id);
    if (payload != null && payload.Operations != null) {
      for (ScimPatchOperation op : payload.Operations) {
        if (op.value instanceof List<?> list) {
          for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Object value = map.get("value");
            if (value == null) continue;
            Long userId = Long.parseLong(String.valueOf(value));
            OrganizationMemberEntity membership = members.findByOrgIdAndUserId(principal.orgId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            membership.setRole(role);
            membership.setUpdatedAt(Instant.now());
            members.save(membership);
          }
        }
      }
    }
    return scimGroup(principal.orgId(), role);
  }

  private OrganizationMemberEntity upsertMembership(Long orgId, UserEntity user, String role, Boolean active) {
    OrganizationMemberEntity membership = members.findByOrgIdAndUserId(orgId, user.getId()).orElse(null);
    if (membership == null) {
      membership = new OrganizationMemberEntity();
      membership.setOrgId(orgId);
      membership.setUserId(user.getId());
      membership.setRole(organizations.normalizeOrgRole(role));
      membership.setStatus(active != null && !active ? "SUSPENDED" : "ACTIVE");
      membership.setCreatedAt(Instant.now());
    } else {
      membership.setRole(organizations.normalizeOrgRole(role));
      if (active != null) {
        membership.setStatus(active ? "ACTIVE" : "SUSPENDED");
      }
      membership.setUpdatedAt(Instant.now());
    }
    members.save(membership);
    if (user.getPrimaryOrgId() == null) {
      user.setPrimaryOrgId(orgId);
      users.save(user);
    }
    return membership;
  }

  private void applyActive(UserEntity user, Boolean active) {
    if (active == null) return;
    if (active) {
      user.setStatus("ACTIVE");
      user.setDisabledAt(null);
    } else {
      user.setStatus("DISABLED");
      user.setDisabledAt(Instant.now());
    }
  }

  private Map<String, Object> toScimUser(UserEntity user, OrganizationMemberEntity membership) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
    out.put("id", String.valueOf(user.getId()));
    out.put("userName", user.getEmail());
    out.put("active", user.getStatus() == null || "ACTIVE".equalsIgnoreCase(user.getStatus()));
    out.put("emails", List.of(Map.of("value", user.getEmail(), "primary", true)));
    out.put("roles", List.of(Map.of("value", membership.getRole())));
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("resourceType", "User");
    out.put("meta", meta);
    return out;
  }

  private Map<String, Object> scimGroup(Long orgId, String role) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));
    out.put("id", role);
    out.put("displayName", role);
    List<Map<String, Object>> membersOut = new ArrayList<>();
    for (OrganizationMemberEntity member : members.findByOrgId(orgId)) {
      if (role.equalsIgnoreCase(member.getRole())) {
        membersOut.add(Map.of("value", String.valueOf(member.getUserId())));
      }
    }
    out.put("members", membersOut);
    return out;
  }

  private String resolveEmail(ScimUserPayload payload) {
    if (payload.userName != null && payload.userName.contains("@")) {
      return payload.userName.trim();
    }
    if (payload.emails != null) {
      for (ScimEmail email : payload.emails) {
        if (email.value != null && email.value.contains("@")) {
          return email.value.trim();
        }
      }
    }
    return null;
  }

  private String resolveRole(ScimUserPayload payload) {
    if (payload.roles != null && !payload.roles.isEmpty()) {
      return payload.roles.get(0).value;
    }
    if (payload.groups != null && !payload.groups.isEmpty()) {
      return payload.groups.get(0).display;
    }
    return "MEMBER";
  }

  private String parseFilterValue(String filter) {
    String[] parts = filter.split(" ");
    for (int i = 0; i < parts.length; i++) {
      if ("eq".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
        String value = parts[i + 1];
        return value.replace("\"", "").trim();
      }
    }
    return null;
  }

  private Boolean extractBoolean(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Map<?, ?> map && map.containsKey("active")) {
      Object v = map.get("active");
      if (v instanceof Boolean b) return b;
      return Boolean.parseBoolean(String.valueOf(v));
    }
    if (value != null) return Boolean.parseBoolean(String.valueOf(value));
    return null;
  }

  private String extractString(Object value) {
    if (value == null) return null;
    if (value instanceof Map<?, ?> map && map.containsKey("value")) {
      Object v = map.get("value");
      return v == null ? null : String.valueOf(v);
    }
    return String.valueOf(value);
  }

  private String extractRole(Object value) {
    if (value instanceof List<?> list && !list.isEmpty()) {
      Object entry = list.get(0);
      if (entry instanceof Map<?, ?> map) {
        Object val = map.get("value");
        if (val == null) val = map.get("display");
        return val == null ? null : String.valueOf(val);
      }
    }
    if (value instanceof Map<?, ?> map) {
      Object val = map.get("value");
      if (val == null) val = map.get("display");
      return val == null ? null : String.valueOf(val);
    }
    return value == null ? null : String.valueOf(value);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ScimUserPayload {
    public List<String> schemas;
    public String userName;
    public ScimName name;
    public List<ScimEmail> emails;
    public Boolean active;
    public List<ScimRole> roles;
    public List<ScimGroup> groups;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ScimName {
    public String givenName;
    public String familyName;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ScimEmail {
    public String value;
    public Boolean primary;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ScimRole {
    public String value;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ScimGroup {
    public String display;
    public String value;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ScimPatchRequest {
    public List<ScimPatchOperation> Operations;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ScimPatchOperation {
    public String op;
    public String path;
    public Object value;
  }
}

package com.alphamath.auth.org;

import com.alphamath.auth.user.UserEntity;
import com.alphamath.auth.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class OrganizationService {
  public static final String ROLE_OWNER = "OWNER";
  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_MEMBER = "MEMBER";
  public static final String ROLE_READ_ONLY = "READ_ONLY";

  private final OrganizationRepository organizations;
  private final OrganizationMemberRepository members;
  private final OrganizationInviteRepository invites;
  private final UserRepository users;

  public OrganizationService(OrganizationRepository organizations,
                             OrganizationMemberRepository members,
                             OrganizationInviteRepository invites,
                             UserRepository users) {
    this.organizations = organizations;
    this.members = members;
    this.invites = invites;
    this.users = users;
  }

  @Transactional
  public OrganizationEntity createOrganization(UserEntity owner, String name) {
    String orgName = (name == null || name.isBlank()) ? defaultOrgName(owner.getEmail()) : name.trim();
    OrganizationEntity org = new OrganizationEntity();
    org.setName(orgName);
    org.setSlug(uniqueSlug(slugify(orgName)));
    org = organizations.save(org);

    OrganizationMemberEntity member = new OrganizationMemberEntity();
    member.setOrgId(org.getId());
    member.setUserId(owner.getId());
    member.setRole(ROLE_OWNER);
    member.setStatus("ACTIVE");
    members.save(member);

    if (owner.getPrimaryOrgId() == null) {
      owner.setPrimaryOrgId(org.getId());
      users.save(owner);
    }

    return org;
  }

  public OrganizationEntity resolveOrganization(UserEntity user, Long requestedOrgId, String requestedSlug) {
    if (requestedOrgId != null) {
      requireMembership(user, requestedOrgId);
      return organizations.findById(requestedOrgId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }
    if (requestedSlug != null && !requestedSlug.isBlank()) {
      OrganizationEntity org = organizations.findBySlug(requestedSlug.trim())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
      requireMembership(user, org.getId());
      return org;
    }
    if (user.getPrimaryOrgId() != null) {
      OrganizationEntity org = organizations.findById(user.getPrimaryOrgId()).orElse(null);
      if (org != null && members.existsByOrgIdAndUserId(org.getId(), user.getId())) {
        return org;
      }
    }
    List<OrganizationMemberEntity> memberships = members.findByUserId(user.getId());
    if (!memberships.isEmpty()) {
      OrganizationMemberEntity first = memberships.get(0);
      OrganizationEntity org = organizations.findById(first.getOrgId()).orElse(null);
      if (org != null) {
        if (user.getPrimaryOrgId() == null) {
          user.setPrimaryOrgId(org.getId());
          users.save(user);
        }
        return org;
      }
    }
    return createOrganization(user, defaultOrgName(user.getEmail()));
  }

  public List<OrganizationMemberEntity> membershipsForUser(Long userId) {
    return members.findByUserId(userId);
  }

  public List<OrganizationMemberEntity> membersForOrg(Long orgId) {
    return members.findByOrgId(orgId);
  }

  public OrganizationMemberEntity requireMembership(UserEntity user, Long orgId) {
    return members.findByOrgIdAndUserId(orgId, user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization access denied"));
  }

  public void requireOrgRole(UserEntity user, Long orgId, String requiredRole) {
    OrganizationMemberEntity membership = requireMembership(user, orgId);
    if (!hasOrgRole(membership.getRole(), requiredRole)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization role required");
    }
  }

  public boolean hasOrgRole(String role, String required) {
    if (required == null || required.isBlank()) return true;
    String needle = required.trim().toUpperCase(Locale.US);
    String current = role == null ? "" : role.trim().toUpperCase(Locale.US);
    if (current.equals(ROLE_OWNER)) return true;
    if (current.equals(ROLE_ADMIN) && !needle.equals(ROLE_OWNER)) return true;
    return current.equals(needle);
  }

  @Transactional
  public OrganizationInviteEntity invite(UserEntity actor, Long orgId, String email, String role, Instant expiresAt) {
    requireOrgRole(actor, orgId, ROLE_ADMIN);
    String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.US);
    if (normalizedEmail == null || normalizedEmail.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite email required");
    }
    OrganizationInviteEntity invite = invites.findByOrgIdAndEmail(orgId, normalizedEmail).orElse(null);
    if (invite != null && "PENDING".equalsIgnoreCase(invite.getStatus())) {
      return invite;
    }
    OrganizationInviteEntity created = new OrganizationInviteEntity();
    created.setOrgId(orgId);
    created.setEmail(normalizedEmail);
    created.setRole(normalizeOrgRole(role));
    created.setToken(java.util.UUID.randomUUID().toString().replace("-", ""));
    created.setStatus("PENDING");
    created.setExpiresAt(expiresAt);
    return invites.save(created);
  }

  @Transactional
  public OrganizationMemberEntity acceptInvite(UserEntity user, String token) {
    OrganizationInviteEntity invite = invites.findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));
    if (!"PENDING".equalsIgnoreCase(invite.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite is not active");
    }
    if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.GONE, "Invite expired");
    }
    String email = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase(Locale.US);
    if (!email.equals(invite.getEmail())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invite email does not match user");
    }
    OrganizationMemberEntity membership = members.findByOrgIdAndUserId(invite.getOrgId(), user.getId()).orElse(null);
    if (membership == null) {
      membership = new OrganizationMemberEntity();
      membership.setOrgId(invite.getOrgId());
      membership.setUserId(user.getId());
      membership.setRole(invite.getRole());
      membership.setStatus("ACTIVE");
      membership = members.save(membership);
    }
    invite.setStatus("ACCEPTED");
    invite.setAcceptedAt(Instant.now());
    invites.save(invite);
    if (user.getPrimaryOrgId() == null) {
      user.setPrimaryOrgId(invite.getOrgId());
      users.save(user);
    }
    return membership;
  }

  public OrganizationMemberEntity updateMemberRole(UserEntity actor, Long orgId, Long userId, String role) {
    requireOrgRole(actor, orgId, ROLE_ADMIN);
    OrganizationMemberEntity membership = members.findByOrgIdAndUserId(orgId, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
    membership.setRole(normalizeOrgRole(role));
    membership.setUpdatedAt(Instant.now());
    return members.save(membership);
  }

  public String normalizeOrgRole(String role) {
    if (role == null || role.isBlank()) {
      return ROLE_MEMBER;
    }
    String normalized = role.trim().toUpperCase(Locale.US);
    return switch (normalized) {
      case ROLE_OWNER, ROLE_ADMIN, ROLE_MEMBER, ROLE_READ_ONLY -> normalized;
      default -> ROLE_MEMBER;
    };
  }

  private String defaultOrgName(String email) {
    if (email == null || email.isBlank()) {
      return "Personal Organization";
    }
    String local = email.split("@", 2)[0];
    if (local == null || local.isBlank()) {
      return "Personal Organization";
    }
    return local + " Organization";
  }

  private String slugify(String input) {
    if (input == null || input.isBlank()) {
      return "org";
    }
    String slug = input.trim().toLowerCase(Locale.US)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+", "")
        .replaceAll("-+$", "");
    return slug.isBlank() ? "org" : slug;
  }

  private String uniqueSlug(String base) {
    String slug = base;
    int suffix = 1;
    while (organizations.existsBySlug(slug)) {
      slug = base + "-" + suffix;
      suffix += 1;
    }
    return slug;
  }

}

package com.alphamath.auth.web;

import com.alphamath.auth.org.OrganizationMemberEntity;
import com.alphamath.auth.org.OrganizationMemberRepository;
import com.alphamath.auth.org.OrganizationService;
import com.alphamath.auth.scim.ScimAuthService;
import com.alphamath.auth.user.UserEntity;
import com.alphamath.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScimContractTest {

  private MockMvc mvc;
  private StubScimAuthService scimAuth;
  private OrganizationService organizations;

  @Mock
  private UserRepository users;
  @Mock
  private OrganizationMemberRepository members;

  @BeforeEach
  void setUp() {
    scimAuth = new StubScimAuthService();
    organizations = new OrganizationService(null, null, null, null);
    ScimController controller = new ScimController(scimAuth, users, members, organizations);
    mvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void serviceProviderConfigReturnsContractShape() throws Exception {
    scimAuth.principal = new ScimAuthService.ScimPrincipal(7L, "acme");

    mvc.perform(get("/api/v1/scim/v2/ServiceProviderConfig")
            .header("Authorization", "Bearer scim-valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"))
        .andExpect(jsonPath("$.patch.supported").value(true))
        .andExpect(jsonPath("$.filter.supported").value(true));
  }

  @Test
  void createUserProvisioningMatchesFixtureContract() throws Exception {
    scimAuth.principal = new ScimAuthService.ScimPrincipal(7L, "acme");
    when(users.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
    when(users.save(any(UserEntity.class))).thenAnswer(invocation -> {
      UserEntity entity = invocation.getArgument(0);
      if (entity.getId() == null) {
        entity.setId(42L);
      }
      return entity;
    });
    when(members.findByOrgIdAndUserId(7L, 42L)).thenReturn(Optional.empty());
    when(members.save(any(OrganizationMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    mvc.perform(post("/api/v1/scim/v2/Users")
            .header("Authorization", "Bearer scim-valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("fixtures/scim/user-create-request.json")))
        .andExpect(status().isOk())
        .andExpect(content().json(fixture("fixtures/scim/user-create-response.json")));
  }

  @Test
  void patchGroupProvisioningMatchesFixtureContract() throws Exception {
    OrganizationMemberEntity membership = new OrganizationMemberEntity();
    membership.setOrgId(7L);
    membership.setUserId(42L);
    membership.setRole("MEMBER");
    membership.setStatus("ACTIVE");

    scimAuth.principal = new ScimAuthService.ScimPrincipal(7L, "acme");
    when(members.findByOrgIdAndUserId(7L, 42L)).thenReturn(Optional.of(membership));
    when(members.save(any(OrganizationMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(members.findByOrgId(7L)).thenReturn(List.of(membership));

    mvc.perform(patch("/api/v1/scim/v2/Groups/ADMIN")
            .header("Authorization", "Bearer scim-valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixture("fixtures/scim/group-patch-request.json")))
        .andExpect(status().isOk())
        .andExpect(content().json(fixture("fixtures/scim/group-patch-response.json")));
  }

  @Test
  void listUsersRejectsInvalidScimToken() throws Exception {
    scimAuth.exception = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SCIM token");

    mvc.perform(get("/api/v1/scim/v2/Users")
            .header("Authorization", "Bearer bad-token"))
        .andExpect(status().isUnauthorized());
  }

  private String fixture(String path) throws IOException {
    ClassPathResource resource = new ClassPathResource(path);
    return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
  }

  private static class StubScimAuthService extends ScimAuthService {
    private ScimPrincipal principal;
    private ResponseStatusException exception;

    StubScimAuthService() {
      super(null, null);
    }

    @Override
    public ScimPrincipal authenticate(String authHeader) {
      if (exception != null) {
        throw exception;
      }
      if (principal == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SCIM token");
      }
      return principal;
    }
  }
}

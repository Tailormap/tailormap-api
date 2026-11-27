/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.OIDCConfiguration;
import org.tailormap.api.repository.GroupRepository;
import org.tailormap.api.repository.OIDCConfigurationRepository;

class OIDCAuthenticationEventsHandlerTest {
  private static AuthenticationSuccessEvent loginEvent(List<String> roles) {
    ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("1")
        .clientId("test-client")
        .clientName("Test IdP")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/api/oauth2/callback")
        .authorizationUri("https://idp.example.com/authorize")
        .tokenUri("https://idp.example.com/token")
        .build();
    OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
        .subject("user")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("roles", roles)
        .build();
    DefaultOidcUser oidcUser = new DefaultOidcUser(List.of(), idToken);
    OAuth2AuthorizationExchange exchange = new OAuth2AuthorizationExchange(
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://idp.example.com/authorize")
            .clientId("test-client")
            .redirectUri("http://localhost/api/oauth2/callback")
            .build(),
        OAuth2AuthorizationResponse.success("code")
            .redirectUri("http://localhost/api/oauth2/callback")
            .build());
    OAuth2AccessToken accessToken = new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "access-token",
        Instant.now(),
        Instant.now().plusSeconds(60));
    return new AuthenticationSuccessEvent(
        new OAuth2LoginAuthenticationToken(clientRegistration, exchange, oidcUser, List.of(), accessToken));
  }

  private static List<String> savedGroupNames(
      OIDCConfiguration oidcConfiguration, List<String> roles, int expectedSaves) {
    GroupRepository groupRepository = mock(GroupRepository.class);
    when(groupRepository.findById(anyString())).thenReturn(Optional.empty());
    OIDCConfigurationRepository oidcConfigurationRepository = mock(OIDCConfigurationRepository.class);
    when(oidcConfigurationRepository.findById(any())).thenReturn(Optional.of(oidcConfiguration));

    new OIDCAuthenticationEventsHandler(groupRepository, oidcConfigurationRepository).onSuccess(loginEvent(roles));

    ArgumentCaptor<Group> groups = ArgumentCaptor.forClass(Group.class);
    verify(groupRepository, times(expectedSaves)).save(groups.capture());
    return groups.getAllValues().stream().map(Group::getName).toList();
  }

  @Test
  void skips_roles_that_are_not_valid_group_names() {
    assertEquals(
        List.of("valid_role"),
        savedGroupNames(
            new OIDCConfiguration(),
            List.of("valid_role", "Domain Users", "App.Read", "x".repeat(256)),
            1));
  }

  @Test
  void only_creates_groups_for_roles_matching_the_roles_claim_filter() {
    assertEquals(
        List.of("tailormap-viewer"),
        savedGroupNames(
            new OIDCConfiguration().setRolesClaimFilterRegex("^tailormap-"),
            List.of("tailormap-viewer", "other-app"),
            1));
  }
}

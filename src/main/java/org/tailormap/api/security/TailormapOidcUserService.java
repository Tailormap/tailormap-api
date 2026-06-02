/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.tailormap.api.persistence.OIDCConfiguration;
import org.tailormap.api.repository.GroupRepository;
import org.tailormap.api.repository.OIDCConfigurationRepository;

@Service
public class TailormapOidcUserService extends OidcUserService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final GroupRepository groupRepository;
  private final OIDCConfigurationRepository oidcConfigurationRepository;

  public TailormapOidcUserService(
      GroupRepository groupRepository, OIDCConfigurationRepository oidcConfigurationRepository) {
    this.groupRepository = groupRepository;
    this.oidcConfigurationRepository = oidcConfigurationRepository;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser user = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    Optional<OIDCConfiguration> oidcConfiguration = "static".equals(registrationId)
        ? Optional.empty()
        : oidcConfigurationRepository.findById(Long.valueOf(registrationId));

    Set<String> groups = new HashSet<>();

    List<String> roles = userRequest.getIdToken().getClaimAsStringList("roles");
    if (roles != null) {
      String oidcRegistrationName = userRequest.getClientRegistration().getClientName();
      logger.debug("[{}] Roles from ID token: {}", oidcRegistrationName, roles);
      Pattern rolesClaimFilterPattern = oidcConfiguration
          .map(OIDCConfiguration::getRolesClaimFilterRegex)
          .map(Pattern::compile)
          .orElse(null);

      if (rolesClaimFilterPattern != null) {
        List<String> filteredOut = roles.stream()
            .filter(role -> !rolesClaimFilterPattern.matcher(role).find())
            .toList();
        if (logger.isDebugEnabled() && !filteredOut.isEmpty()) {
          logger.debug(
              "[{}] Roles filtered out by pattern '{}': {}",
              oidcRegistrationName,
              rolesClaimFilterPattern,
              filteredOut);
        }
        roles.removeAll(filteredOut);
      }
      groups.addAll(roles);
    }

    // Add default authorities for OIDC registration
    if (oidcConfiguration.isPresent() && oidcConfiguration.get().getDefaultAuthorities() != null) {
      groups.addAll(oidcConfiguration.orElseThrow().getDefaultAuthorities());
    }

    // Add aliases for groups (with the same name as the role) as authorities, even if the group does not exist
    Set<String> aliases = groupRepository.findAliasesForGroups(groups);
    groups.addAll(aliases);

    // Find all additional properties for the groups, taking aliases into account
    Collection<TailormapAdditionalProperty> groupProperties =
        groupRepository.findAdditionalPropertiesByGroups(groups);

    // Adds OIDC_USER and SCOPE_* authorities
    Collection<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
    // Add (aliased) group names as authorities
    groups.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);

    String userNameAttributeName = userRequest
        .getClientRegistration()
        .getProviderDetails()
        .getUserInfoEndpoint()
        .getUserNameAttributeName();

    String oidcRegistrationName = userRequest.getClientRegistration().getClientName();

    return new TailormapOidcUser(
        authorities,
        user.getIdToken(),
        user.getUserInfo(),
        userNameAttributeName,
        oidcRegistrationName,
        groupProperties);
  }
}

/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.OIDCConfiguration;
import org.tailormap.api.repository.GroupRepository;
import org.tailormap.api.repository.OIDCConfigurationRepository;
import org.tailormap.api.util.Constants;

@Component
public class OIDCAuthenticationEventsHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern GROUP_NAME_PATTERN = Pattern.compile(Constants.NAME_REGEX);

  /** Length of the groups.name column. */
  private static final int GROUP_NAME_MAX_LENGTH = 255;

  private final GroupRepository groupRepository;
  private final OIDCConfigurationRepository oidcConfigurationRepository;

  public OIDCAuthenticationEventsHandler(
      GroupRepository groupRepository, OIDCConfigurationRepository oidcConfigurationRepository) {
    this.groupRepository = groupRepository;
    this.oidcConfigurationRepository = oidcConfigurationRepository;
  }

  @EventListener
  @Transactional
  public void onSuccess(AuthenticationSuccessEvent success) {
    if (success.getSource() instanceof OAuth2LoginAuthenticationToken token
        && token.getPrincipal() instanceof DefaultOidcUser oidcUser) {
      String registrationId = token.getClientRegistration().getRegistrationId();
      String clientId = token.getClientRegistration().getClientId();

      OIDCConfiguration oidcConfiguration = null;
      if (!"static".equals(registrationId)) {
        try {
          oidcConfiguration = oidcConfigurationRepository
              .findById(Long.parseLong(registrationId))
              .orElse(null);
        } catch (NumberFormatException ignored) {
          oidcConfiguration = null;
        }
      }
      boolean createNewGroup = oidcConfiguration == null || !oidcConfiguration.isDisableAutomaticGroupCreation();

      List<String> roles = TailormapOidcUserService.filterRoles(
          Optional.ofNullable(oidcUser.getIdToken().getClaimAsStringList("roles"))
              .orElseGet(Collections::emptyList),
          oidcConfiguration == null ? null : oidcConfiguration.getRolesClaimFilterRegex(),
          token.getClientRegistration().getClientName());

      for (String role : roles) {
        if (!isValidGroupName(role)) {
          // Saving a group with this name would fail and prevent logging in
          logger.warn(
              "Role \"{}\" from OIDC client ID {} is not a valid group name, not creating or updating a group for it",
              role,
              clientId);
          continue;
        }
        Group group = groupRepository
            .findById(role)
            .orElseGet(() -> createNewGroup ? new Group().setName(role) : null);
        if (group != null) {
          group.oidcClientIdSeen(clientId);
          groupRepository.save(group);
        }
      }
    }
  }

  private static boolean isValidGroupName(String name) {
    return name.length() <= GROUP_NAME_MAX_LENGTH
        && GROUP_NAME_PATTERN.matcher(name).matches();
  }
}

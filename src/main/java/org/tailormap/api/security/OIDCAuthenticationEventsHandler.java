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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.repository.GroupRepository;
import org.tailormap.api.security.events.OAuth2AuthenticationFailureEvent;

@Component
public class OIDCAuthenticationEventsHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final GroupRepository groupRepository;

  public OIDCAuthenticationEventsHandler(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

  @EventListener
  @Transactional
  public void onSuccess(AuthenticationSuccessEvent success) {
    if (success.getSource() instanceof OAuth2LoginAuthenticationToken token
        && token.getPrincipal() instanceof DefaultOidcUser oidcUser) {
      String clientId = token.getClientRegistration().getClientId();

      List<String> roles = Optional.ofNullable(oidcUser.getIdToken().getClaimAsStringList("roles"))
          .orElseGet(Collections::emptyList);

      for (String role : roles) {
        Group group = groupRepository.findById(role).orElseGet(() -> new Group().setName(role));
        group.oidcClientIdSeen(clientId);
        groupRepository.save(group);
      }
    }
  }

  @EventListener
  public void onOAuth2AuthenticationFailureEvent(OAuth2AuthenticationFailureEvent event) {
    logger.info(
        "OAuth2 authentication failure: {}, {}", event.getException().getMessage(), event);
  }
}

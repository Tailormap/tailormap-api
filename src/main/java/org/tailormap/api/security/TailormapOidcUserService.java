/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.tailormap.api.repository.GroupRepository;

@Service
public class TailormapOidcUserService extends OidcUserService {
  private final GroupRepository groupRepository;

  public TailormapOidcUserService(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser user = super.loadUser(userRequest);

    List<String> groups = new ArrayList<>();
    Optional.ofNullable(userRequest.getIdToken().getClaimAsStringList("roles"))
        .ifPresent(groups::addAll);

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

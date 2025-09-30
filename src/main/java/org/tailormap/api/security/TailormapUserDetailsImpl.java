/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import java.io.Serial;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.User;
import org.tailormap.api.persistence.json.AdminAdditionalProperty;
import org.tailormap.api.repository.GroupRepository;

/* Do not make public, use the interface */
class TailormapUserDetailsImpl implements TailormapUserDetails {

  @Serial
  private static final long serialVersionUID = 1L;

  private final Collection<GrantedAuthority> authorities;
  private final String username;
  private final String password;
  private final ZonedDateTime validUntil;
  private final boolean enabled;

  private final Collection<TailormapAdditionalProperty> additionalProperties = new ArrayList<>();
  private final Collection<TailormapAdditionalProperty> additionalGroupProperties = new ArrayList<>();

  public TailormapUserDetailsImpl(User user, GroupRepository groupRepository) {
    authorities = new HashSet<>();
    user.getGroups().stream()
        .map(Group::getName)
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);

    user.getGroups().stream()
        .map(Group::getAliasForGroup)
        .filter(StringUtils::isNotBlank)
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);

    username = user.getUsername();
    password = user.getPassword();
    validUntil = user.getValidUntil();
    enabled = user.isEnabled();

    if (user.getAdditionalProperties() != null) {
      for (AdminAdditionalProperty property : user.getAdditionalProperties()) {
        additionalProperties.add(new TailormapAdditionalProperty(
            property.getKey(), property.getIsPublic(), property.getValue()));
      }
    }

    // For group properties, look in the database with a list of authorities instead of user.getGroups(), so
    // aliasForGroup is taken into account
    this.additionalGroupProperties.addAll(groupRepository.findAdditionalPropertiesByGroups(
        authorities.stream().map(GrantedAuthority::getAuthority).toList()));
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return validUntil == null || validUntil.isAfter(ZonedDateTime.now(ZoneId.systemDefault()));
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public Collection<TailormapAdditionalProperty> getAdditionalProperties() {
    return additionalProperties;
  }

  @Override
  public Collection<TailormapAdditionalProperty> getAdditionalGroupProperties() {
    return additionalGroupProperties;
  }
}

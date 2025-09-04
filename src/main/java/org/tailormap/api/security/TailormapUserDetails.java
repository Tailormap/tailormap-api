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
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.User;
import org.tailormap.api.repository.GroupRepository;

public class TailormapUserDetails implements UserDetails {
  public record UDAdditionalProperty(String key, Boolean isPublic, Object value) {}

  @Serial
  private static final long serialVersionUID = 2L;

  private final Collection<GrantedAuthority> authorities;
  private final String username;
  private final String password;
  private final ZonedDateTime validUntil;
  private final boolean enabled;

  private final List<UDAdditionalProperty> additionalProperties = new ArrayList<>();
  private final List<UDAdditionalProperty> additionalGroupProperties = new ArrayList<>();

  public TailormapUserDetails(User user, GroupRepository groupRepository) {
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

    user.getAdditionalProperties().stream()
        .map(p -> new UDAdditionalProperty(p.getKey(), p.getIsPublic(), p.getValue()))
        .forEach(additionalProperties::add);

    // For group properties, look in the database instead of user.getGroups(), so aliasForGroup is taken into
    // account
    groupRepository
        .findAllById(
            authorities.stream().map(GrantedAuthority::getAuthority).toList())
        .stream()
        .map(Group::getAdditionalProperties)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .map(p -> new UDAdditionalProperty(p.getKey(), p.getIsPublic(), p.getValue()))
        .forEach(additionalGroupProperties::add);
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

  public List<UDAdditionalProperty> getAdditionalProperties() {
    return additionalProperties;
  }

  public List<UDAdditionalProperty> getAdditionalGroupProperties() {
    return additionalGroupProperties;
  }

  public boolean getBooleanUserProperty(String key) {
    return additionalProperties.stream()
        .filter(p -> p.key.equals(key))
        .map(UDAdditionalProperty::value)
        .map("true"::equals)
        .findFirst()
        .orElse(false);
  }
}

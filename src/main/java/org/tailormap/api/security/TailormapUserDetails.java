/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import java.io.Serial;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.User;

public class TailormapUserDetails implements UserDetails {
  @Serial
  private static final long serialVersionUID = 1L;

  private final Collection<GrantedAuthority> authorities;
  private final String username;
  private final String password;
  private final ZonedDateTime validUntil;
  private final boolean enabled;

  private final List<Map.Entry<String, Object>> additionalProperties = new ArrayList<>();

  public TailormapUserDetails(User user) {
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
        .map(p -> new AbstractMap.SimpleImmutableEntry<>(p.getKey(), p.getValue()))
        .forEach(additionalProperties::add);
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

  public List<Map.Entry<String, Object>> getAdditionalProperties() {
    return additionalProperties;
  }
}

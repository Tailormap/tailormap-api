/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.User;
import org.tailormap.api.persistence.json.AdminAdditionalProperty;

public class TailormapUserDetails implements UserDetails {

  private final User user;

  public TailormapUserDetails(User user) {
    this.user = user;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    Collection<GrantedAuthority> authorities = new HashSet<>();
    user.getGroups().stream()
        .map(Group::getName)
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);

    user.getGroups().stream()
        .map(Group::getAliasForGroup)
        .filter(StringUtils::isNotBlank)
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);
    return authorities;
  }

  @Override
  public String getPassword() {
    return user.getPassword();
  }

  @Override
  public String getUsername() {
    return user.getUsername();
  }

  @Override
  public boolean isAccountNonExpired() {
    return user.getValidUntil() == null || user.getValidUntil().isAfter(ZonedDateTime.now(ZoneId.systemDefault()));
  }

  @Override
  public boolean isEnabled() {
    return user.isEnabled();
  }

  public List<AdminAdditionalProperty> getAdditionalProperties() {
    return user.getAdditionalProperties();
  }
}

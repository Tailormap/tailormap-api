/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.stream.Collectors;

public class TailormapUserDetails implements UserDetails {

  private final User user;

  public TailormapUserDetails(User user) {
    this.user = user;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return user.getGroups().stream()
        .map(Group::getName)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toSet());
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
    return user.getValidUntil() == null
        || user.getValidUntil().isAfter(ZonedDateTime.now(ZoneId.systemDefault()));
  }

  @Override
  public boolean isAccountNonLocked() {
    return !user.isLocked();
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return user.isEnabled();
  }
}

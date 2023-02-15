/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.security;

import java.util.Collection;
import java.util.List;
import nl.b3p.tailormap.api.persistence.Group;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class InternalAdminAuthentication implements Authentication {
  public static final InternalAdminAuthentication INSTANCE = new InternalAdminAuthentication();

  /**
   * Allow usage of secured methods such as JpaRepositories as admin, for example on startup or in
   * background tasks.
   */
  public static void setInSecurityContext() {
    SecurityContext context = SecurityContextHolder.getContext();
    context.setAuthentication(INSTANCE);
    SecurityContextHolder.setContext(context);
  }

  public static void clearSecurityContextAuthentication() {
    SecurityContext context = SecurityContextHolder.getContext();
    context.setAuthentication(null);
    SecurityContextHolder.setContext(context);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(Group.ADMIN));
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getDetails() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return null;
  }

  @Override
  public boolean isAuthenticated() {
    return true;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
    throw new IllegalArgumentException();
  }

  @Override
  public String getName() {
    return "backend-internal-admin";
  }
}

/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

public class TailormapOidcUser extends DefaultOidcUser implements TailormapUserDetails {
  @Serial
  private static final long serialVersionUID = 1L;

  private final Collection<TailormapAdditionalProperty> additionalGroupProperties = new ArrayList<>();

  private final String oidcRegistrationName;

  public TailormapOidcUser(
      Collection<? extends GrantedAuthority> authorities,
      OidcIdToken idToken,
      OidcUserInfo userInfo,
      String nameAttributeKey,
      String oidcRegistrationName,
      Collection<TailormapAdditionalProperty> additionalGroupProperties) {
    super(authorities, idToken, userInfo, nameAttributeKey);
    this.oidcRegistrationName = oidcRegistrationName;
    if (additionalGroupProperties != null) {
      this.additionalGroupProperties.addAll(additionalGroupProperties);
    }
  }

  @Override
  public Collection<TailormapAdditionalProperty> getAdditionalGroupProperties() {
    return Collections.unmodifiableCollection(additionalGroupProperties);
  }

  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public String getUsername() {
    return super.getName();
  }

  @Override
  public String getOrganisation() {
    return oidcRegistrationName;
  }
}

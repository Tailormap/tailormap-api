/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

public class TailormapOidcUser extends DefaultOidcUser implements TailormapUserDetails {
  @Serial
  private static final long serialVersionUID = 1L;

  private final Collection<TailormapAdditionalProperty> additionalGroupProperties = new ArrayList<>();

  private final String oidcRegistrationName;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public TailormapOidcUser(
      @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
      @JsonProperty("idToken") OidcIdToken idToken,
      @JsonProperty("userInfo") OidcUserInfo userInfo,
      @JsonProperty("nameAttributeKey") String nameAttributeKey,
      @JsonProperty("oidcRegistrationName") String oidcRegistrationName,
      @JsonProperty("additionalGroupProperties")
          Collection<TailormapAdditionalProperty> additionalGroupProperties) {
    super(authorities, idToken, userInfo, nameAttributeKey);
    this.oidcRegistrationName = oidcRegistrationName;
    if (additionalGroupProperties != null) {
      this.additionalGroupProperties.addAll(additionalGroupProperties);
    }
  }

  @Override
  public Collection<TailormapAdditionalProperty> getAdditionalGroupProperties() {
    return additionalGroupProperties;
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

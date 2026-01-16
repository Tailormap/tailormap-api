/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.ZonedDateTime;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TailormapUserDetailsImplMixin {

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public TailormapUserDetailsImplMixin(
      @SuppressWarnings("unused") @JsonProperty("authorities") Collection<GrantedAuthority> authorities,
      @SuppressWarnings("unused") @JsonProperty("validUntil") ZonedDateTime validUntil,
      @SuppressWarnings("unused") @JsonProperty("username") String username,
      @SuppressWarnings("unused") @JsonProperty("organisation") String organisation,
      @SuppressWarnings("unused") @JsonProperty("password") String password,
      @SuppressWarnings("unused") @JsonProperty("enabled") boolean enabled,
      @SuppressWarnings("unused") @JsonProperty("additionalProperties")
          Collection<TailormapAdditionalProperty> additionalProperties,
      @SuppressWarnings("unused") @JsonProperty("additionalGroupProperties")
          Collection<TailormapAdditionalProperty> additionalGroupProperties) {
    // mixin constructor only for Jackson; no implementation
  }

  /** prevents serializing the password. */
  @JsonIgnore
  public abstract String getPassword();
}

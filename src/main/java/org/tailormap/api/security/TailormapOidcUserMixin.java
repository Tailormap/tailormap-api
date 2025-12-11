/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TailormapOidcUserMixin {

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public TailormapOidcUserMixin(
      @SuppressWarnings("unused") @JsonProperty("claims") Map<String, Object> claims,
      @SuppressWarnings("unused") @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
      @SuppressWarnings("unused") @JsonProperty("attributes") Map<String, Object> attributes,
      @SuppressWarnings("unused") @JsonProperty("nameAttributeKey") String nameAttributeKey,
      @SuppressWarnings("unused") @JsonProperty("oidcRegistrationName") String oidcRegistrationName,
      @SuppressWarnings("unused") @JsonProperty("additionalGroupProperties")
          Collection<TailormapAdditionalProperty> additionalGroupProperties) {
    // mixin constructor only for Jackson 2; no implementation
  }
}

/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import com.fasterxml.jackson.annotation.JsonProperty;
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

/**
 * Internal {@link TailormapUserDetails} implementation.
 *
 * <p>Do not use this class directly; always depend on the {@link TailormapUserDetails} interface instead. This class is
 * {@code public} only to support JSON/session deserialization (e.g. by Jackson / Spring Session). Changing its
 * visibility back to package-private would break that deserialization.
 */
public class TailormapUserDetailsImpl implements TailormapUserDetails {

  @Serial
  private static final long serialVersionUID = 1L;

  private final Collection<GrantedAuthority> authorities;
  private final String username;
  private final String password;
  private final ZonedDateTime validUntil;
  private final boolean enabled;
  private final String organisation;

  private final Collection<TailormapAdditionalProperty> additionalProperties = new ArrayList<>();
  private final Collection<TailormapAdditionalProperty> additionalGroupProperties = new ArrayList<>();

  /**
   * Constructor for Jackson deserialization.
   *
   * @param authorities the authorities
   * @param username the username
   * @param password the password
   * @param validUntil the valid until date
   * @param enabled whether the user is enabled
   * @param organisation the organisation
   */
  @SuppressWarnings("unused")
  TailormapUserDetailsImpl(
      @JsonProperty("authorities") Collection<GrantedAuthority> authorities,
      @JsonProperty("username") String username,
      @JsonProperty("password") String password,
      @JsonProperty("validUntil") ZonedDateTime validUntil,
      @JsonProperty("enabled") boolean enabled,
      @JsonProperty("organisation") String organisation) {
    this.authorities = authorities;
    this.username = username;
    this.password = password;
    this.validUntil = validUntil;
    this.enabled = enabled;
    this.organisation = organisation;
  }

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
    organisation = user.getOrganisation();

    if (user.getAdditionalProperties() != null) {
      for (AdminAdditionalProperty property : user.getAdditionalProperties()) {
        additionalProperties.add(new TailormapAdditionalProperty(
            property.getKey(), property.getIsPublic(), property.getValue()));
      }
    }

    if (groupRepository != null) {
      // For group properties, look in the database with a list of authorities instead of user.getGroups(), so
      // aliasForGroup is taken into account
      this.additionalGroupProperties.addAll(groupRepository.findAdditionalPropertiesByGroups(
          authorities.stream().map(GrantedAuthority::getAuthority).toList()));
    }
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
  public String getOrganisation() {
    return organisation;
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

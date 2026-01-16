/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;
import org.springframework.security.core.userdetails.UserDetails;

public interface TailormapUserDetails extends Serializable, UserDetails {

  default Collection<TailormapAdditionalProperty> getAdditionalProperties() {
    return Collections.emptyList();
  }

  Collection<TailormapAdditionalProperty> getAdditionalGroupProperties();

  /**
   * Returns true if any user or group Boolean property with the given key is true. If beside a true value, there are
   * also properties with the same key but with any other value than true, the true value has precedence.
   *
   * @param key the key to look for
   * @return true if a Boolean property with the key is present with a true value
   */
  default boolean hasTruePropertyForKey(String key) {
    return streamAllPropertiesForKey(key).anyMatch(Boolean.TRUE::equals);
  }

  default Stream<Object> streamAllPropertiesForKey(String key) {
    return Stream.concat(getAdditionalProperties().stream(), getAdditionalGroupProperties().stream())
        .filter(p -> p.key().equals(key))
        .map(TailormapAdditionalProperty::value);
  }

  String getOrganisation();
}

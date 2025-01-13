/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import java.util.List;
import java.util.function.Function;
import org.tailormap.api.persistence.json.AdminAdditionalProperty;

public class AdminAdditionalPropertyHelper {
  public static void addOrUpdateAdminProperty(
      List<AdminAdditionalProperty> list, String key, Object value, boolean isPublic) {
    if (key == null) {
      return;
    }
    list.removeIf(p -> p.getKey().equals(key));
    list.add(new AdminAdditionalProperty().key(key).value(value).isPublic(isPublic));
  }

  public static void mapAdminPropertyValue(
      List<AdminAdditionalProperty> additionalProperties,
      String key,
      boolean isPublic,
      Function<Object, Object> valueMapper) {
    if (key == null) {
      return;
    }
    AdminAdditionalProperty property = additionalProperties.stream()
        .filter(p -> p.getKey().equals(key))
        .findFirst()
        .orElseGet(() -> {
          AdminAdditionalProperty newProperty =
              new AdminAdditionalProperty().key(key).isPublic(isPublic);
          additionalProperties.add(newProperty);
          return newProperty;
        });
    property.setValue(valueMapper.apply(property.getValue()));
  }
}

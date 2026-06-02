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
  // these are known keys used in this API and other projects, do not reuse them for other purposes:
  //      used in Drawing
  public static final String KEY_DRAWINGS_ADMIN = "drawings-admin";
  public static final String KEY_DRAWINGS_READ_ALL = "drawings-read-all";
  //      used in PlanmonitorWonen:
  // -
  // https://github.com/B3Partners/tailormap-planmonitor-wonen/blob/fbe5dd34d64ab0a2b61e1f5db558c7c147d81587/projects/planmonitor-wonen/src/lib/services/planmonitor-authentication.service.ts#L26-L27
  // -
  // https://github.com/B3Partners/planmonitor-wonen-api/blob/d66180f4b015e1e4e7800f8f0fbe1f3450f1ae80/src/main/java/nl/b3p/planmonitorwonen/api/model/auth/PlanmonitorAuthentication.java#L63-L64
  public static final String KEY_TYPE_GEBRUIKER = "typeGebruiker";
  public static final String KEY_GEMEENTE = "gemeente";

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

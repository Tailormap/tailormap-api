/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.helper;

import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import nl.b3p.tailormap.api.persistence.json.TMGeometryType;

public class TMAttributeTypeHelper {
  public static boolean isGeometry(TMAttributeType attributeType) {
    for (TMGeometryType gt : TMGeometryType.values()) {
      if (gt.toString().equals(attributeType.toString())) {
        return true;
      }
    }
    return false;
  }
}

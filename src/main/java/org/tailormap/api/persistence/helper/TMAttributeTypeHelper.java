/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import org.tailormap.api.persistence.json.TMAttributeType;
import org.tailormap.api.persistence.json.TMGeometryType;

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

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.helper;

import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import java.util.Set;

public class TMAttributeTypeHelper {
  private static final Set<TMAttributeType> geometryTypes =
      Set.of(
          TMAttributeType.GEOMETRY,
          TMAttributeType.GEOMETRY_COLLECTION,
          TMAttributeType.POINT,
          TMAttributeType.MULTIPOINT,
          TMAttributeType.LINESTRING,
          TMAttributeType.MULTILINESTRING,
          TMAttributeType.POLYGON,
          TMAttributeType.MULTIPOLYGON);

  public static boolean isGeometry(TMAttributeType attributeType) {
    return geometryTypes.contains(attributeType);
  }
}
